package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.SecurityDepositBalanceRepo;
import com.yuexin.bmp.bi.common.SecurityDepositBalance;
import com.yuexin.bmp.bi.service.SecurityDepositBalanceService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author makejava
 * @since 2025-07-25 16:47:10
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class SecurityDepositBalanceServiceImpl implements SecurityDepositBalanceService, NullHandler {

    private final SecurityDepositBalanceRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<SecurityDepositBalance> query(QueryParams params) throws BusinessException {
        return page(params.getPage(), buildQuery(params));
    }

    @Override
    public PageResponse<SecurityDepositBalance> groupQuery(QueryParams params) throws BusinessException {
        QueryWrapper<SecurityDepositBalance> queryWrapper = buildQuery(params);
        if (CollectionUtils.isEmpty(params.getGroups())) {
            return page(params.getPage(), queryWrapper);
        } else {
            queryWrapper.groupBy(params.getGroups());
            List<String> select = getSelect(params.getGroups());
            queryWrapper.select(String.join(",", select));
            return page(params.getPage(), queryWrapper);
        }
    }

    private List<String> getSelect(List<String> groups) {
        List<String> select = new ArrayList<>(groups);
        select.add("SUM(pay_amount) as pay_amount");
        select.add("SUM(receipted_amount) as receipted_amount");
        select.add("SUM(unreceipted_amount) as unreceipted_amount");
        return select;
    }

    private PageResponse<SecurityDepositBalance> page(PageParams pageParams, QueryWrapper<SecurityDepositBalance> queryWrapper) {
        if (pageParams != null) {
            Page<SecurityDepositBalance> page = this.repo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<SecurityDepositBalance> list = this.repo.selectList(queryWrapper);
            return new PageResponse<>(Long.parseLong(String.valueOf(list.size())), list);
        }
    }

    @Override
    public SecurityDepositBalance sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select(String.join(",", getSelect(new ArrayList<>()))));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(SecurityDepositBalance.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<AddParams> adds = params.stream().collect(Collectors.groupingBy(AddParams::getPartnerType)).entrySet().stream().map(ptEntry ->
                ptEntry.getValue().stream().collect(Collectors.groupingBy(AddParams::getScId)).entrySet().stream().map(scEntry ->
                        scEntry.getValue().stream().collect(Collectors.groupingBy(AddParams::getBusinessBodyId)).entrySet().stream().map(bbEntry -> {
                            AddParams addParams = new AddParams();
                            addParams.setPartnerType(ptEntry.getKey());
                            addParams.setScId(scEntry.getKey());
                            addParams.setBusinessBodyId(bbEntry.getKey());
                            addParams.setPayAmount(bbEntry.getValue().stream().map(AddParams::getPayAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
                            addParams.setReceiptedAmount(bbEntry.getValue().stream().map(AddParams::getReceiptedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
                            addParams.setUnreceiptedAmount(bbEntry.getValue().stream().map(AddParams::getUnreceiptedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
                            addParams.setActualDate(bbEntry.getValue().stream().max(Comparator.comparing(AddParams::getActualDate)).map(AddParams::getActualDate).orElse(null));
                            return addParams;
                        }).collect(Collectors.toList())
                ).flatMap(Collection::stream).collect(Collectors.toList())
        ).flatMap(Collection::stream).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(adds)) {
            return;
        }
        List<Long> ids = uidGenerator.batchGenerate(adds.size());
        List<SecurityDepositBalance> list = new ArrayList<>();
        IntStream.range(0, adds.size()).forEach(i -> {
            SecurityDepositBalance balance = BeanUtils.copyProperties(adds.get(i));
            balance.setId(ids.get(i));
            list.add(balance);
        });
        this.repo.insertBatch(list);
    }

    private QueryWrapper<SecurityDepositBalance> buildQuery(QueryParams params) {
        QueryWrapper<SecurityDepositBalance> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(params.getPartnerType()), "partner_type", params.getPartnerType());
        queryWrapper.eq(params.getScId() != null, "sc_id", params.getScId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getScIds()), "sc_id", params.getScIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        return queryWrapper;
    }
}
