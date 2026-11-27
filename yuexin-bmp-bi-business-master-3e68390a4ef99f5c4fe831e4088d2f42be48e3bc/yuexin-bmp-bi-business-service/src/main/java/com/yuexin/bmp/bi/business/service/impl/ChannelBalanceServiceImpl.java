package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.ChannelBalanceRepo;
import com.yuexin.bmp.bi.common.ChannelBalance;
import com.yuexin.bmp.bi.service.ChannelBalanceService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @author lxf
 * @since 2024-11-20 13:46:15
 */
@Slf4j
@DubboService
public class ChannelBalanceServiceImpl implements ChannelBalanceService {
    @Resource
    private ChannelBalanceRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<ChannelBalance> query(QueryParams params) throws BusinessException {
        return page(params.getPage(), buildQuery(params));
    }

    @Override
    public PageResponse<ChannelBalance> groupQuery(QueryParams params) throws BusinessException {
        if (CollectionUtils.isEmpty(params.getGroups()) || params.getGroups().contains("channel_code")) {
            return page(params.getPage(), buildQuery(params));
        } else {
            QueryWrapper<ChannelBalance> queryWrapper = buildQuery(params);
            queryWrapper.groupBy(params.getGroups());
            List<String> select = getSelect(params.getGroups());
            queryWrapper.select(String.join(",", select));
            return page(params.getPage(), queryWrapper);
        }
    }

    private PageResponse<ChannelBalance> page(PageParams pageParams, QueryWrapper<ChannelBalance> queryWrapper) {
        if (pageParams != null) {
            Page<ChannelBalance> page = this.repo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<ChannelBalance> list = this.repo.selectList(queryWrapper);
            return new PageResponse<>((long) list.size(), list);
        }
    }

    private List<String> getSelect(List<String> groups) {
        List<String> selects = new ArrayList<>(groups);
        selects.add("SUM(all_amount) AS all_amount");
        selects.add("SUM(all_count) AS all_count");
        selects.add("SUM(po_count) AS po_count");
        return selects;
    }

    @Override
    public ChannelBalance sum(QueryParams params) throws BusinessException {
        QueryWrapper<ChannelBalance> queryWrapper = buildQuery(params);
        queryWrapper.select("SUM(all_actual_amount) AS all_actual_amount",
                "SUM(po_actual_amount) AS po_actual_amount",
                "SUM(all_amount) AS all_amount",
                "SUM(all_count) AS all_count",
                "SUM(po_count) AS po_count");
        queryWrapper.last("LIMIT 1");
        return this.repo.selectOne(queryWrapper);
    }

    @Override
    public ChannelBalance get(GetParams params) throws BusinessException {
        QueryWrapper<ChannelBalance> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(params.getChannelCode()), "channel_code", params.getChannelCode());
        if (params.getCreateTime() != null) {
            queryWrapper.eq("create_time", DateUtils.truncate(params.getCreateTime(), Calendar.DAY_OF_MONTH));
        } else {
            queryWrapper.eq("create_time", DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH));
        }
        return this.repo.selectOne(queryWrapper);
    }

    @Override
    public void add(AddParams params) throws BusinessException {
        ChannelBalance channelBalance = BeanUtils.copyProperties(params);
        channelBalance.setId(uidGenerator.generate());
        channelBalance.setUpdateTime(new Date());
        this.repo.insert(channelBalance);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<ChannelBalance> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            ChannelBalance report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            report.setUpdateTime(new Date());
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    private QueryWrapper<ChannelBalance> buildQuery(QueryParams params) {
        QueryWrapper<ChannelBalance> queryWrapper = new QueryWrapper<>();
        if (params.getCreateTime() != null) {
            queryWrapper.eq("create_time", DateUtils.truncate(params.getCreateTime(), Calendar.DAY_OF_MONTH));
        } else {
            queryWrapper.eq("create_time", DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH));
        }
        queryWrapper.eq(params.getChannelId() != null, "channel_id", params.getChannelId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getChannelIds()), "channel_id", params.getChannelIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getChannelCode()), "channel_code", params.getChannelCode());
        queryWrapper.like(StringUtils.isNotBlank(params.getChannelName()), "channel_name", params.getChannelName());
        queryWrapper.eq(params.getSupplierId() != null, "supplier_id", params.getSupplierId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getSupplierIds()), "supplier_id", params.getSupplierIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getSpNumber()), "sp_number", params.getSpNumber());
        queryWrapper.eq(StringUtils.isNotBlank(params.getLandProvince()), "land_province", params.getLandProvince());
        queryWrapper.eq(StringUtils.isNotBlank(params.getLandCity()), "land_city", params.getLandCity());
        queryWrapper.eq(StringUtils.isNotEmpty(params.getAgentName()), "agent_name", params.getAgentName());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getAgentNames()), "agent_name", params.getAgentNames());
        if (params.getSendInfo() != null) {
            if (params.getSendInfo()) {
                queryWrapper.and(w -> w.ne("all_avg_count", 0).or().ne("po_avg_count", 0));
            } else {
                queryWrapper.eq("all_avg_count", 0);
                queryWrapper.eq("po_avg_count", 0);
            }
        }
        queryWrapper.eq("balance_show", true);
        return queryWrapper;
    }
}
