package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.SupplierTradingReportRepo;
import com.yuexin.bmp.bi.common.SupplierTradingReport;
import com.yuexin.bmp.bi.service.SupplierTradingReportService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 供应商交易报表(SupplierTradingReport)表服务实现类
 *
 * @author lxf
 * @since 2024-01-23 10:26:34
 */
@Slf4j
@DubboService
public class SupplierTradingReportServiceImpl implements SupplierTradingReportService, NullHandler {
    @Resource
    private SupplierTradingReportRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<SupplierTradingReport> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<SupplierTradingReport> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<SupplierTradingReport> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>(Long.parseLong(String.valueOf(list.size())), list);
        }
    }

    @Override
    public SupplierTradingReport sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select("SUM(paid_amount) as paidAmount," +
                        "SUM(unpaid_amount) as unpaidAmount," +
                        "SUM(unpaid_amount_sum) as unpaidAmountSum," +
                        "SUM(ticket_amount) as ticketAmount," +
                        "SUM(non_ticket_amount) as nonTicketAmount," +
                        "SUM(payable_amount) as payableAmount," +
                        "SUM(payable_amount_not) as payableAmountNot"));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(SupplierTradingReport.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<SupplierTradingReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            SupplierTradingReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void batchDelete(DeleteParams params) throws BusinessException {
        repo.delete(new QueryWrapper<SupplierTradingReport>().eq("month", params.getMonth()));
    }

    private QueryWrapper<SupplierTradingReport> buildQuery(QueryParams params) {
        QueryWrapper<SupplierTradingReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getSupplierId() != null, "supplier_id", params.getSupplierId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getSupplierIds()), "supplier_id", params.getSupplierIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.ge(params.getStartTime() != null, "month", params.getStartTime());
        queryWrapper.le(params.getEndTime() != null, "month", params.getEndTime());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getSupplierIdList()), "supplier_id", params.getSupplierIdList());
        if (params.getUnpaidAmount() != null) {
            queryWrapper.gt(params.getUnpaidAmount(), "unpaid_amount", 0);
            queryWrapper.eq(!params.getUnpaidAmount(), "unpaid_amount", 0);
        }
        if (params.getNonTicketAmount() != null) {
            queryWrapper.gt(params.getNonTicketAmount(), "non_ticket_amount", 0);
            queryWrapper.eq(!params.getNonTicketAmount(), "non_ticket_amount", 0);
        }
        queryWrapper.orderBy(true, false, Arrays.asList("month", "payable_amount + payable_amount_not"));
        return queryWrapper;
    }
}
