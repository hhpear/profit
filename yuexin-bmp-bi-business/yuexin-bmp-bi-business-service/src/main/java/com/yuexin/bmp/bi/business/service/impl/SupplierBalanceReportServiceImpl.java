package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.SupplierBalanceReportRepo;
import com.yuexin.bmp.bi.common.SupplierBalanceReport;
import com.yuexin.bmp.bi.service.SupplierBalanceReportService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 供应商交易报表(SupplierBalanceReport)表服务实现类
 *
 * @author lxf
 * @since 2024-01-23 10:26:34
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class SupplierBalanceReportServiceImpl implements SupplierBalanceReportService, NullHandler {

    private final SupplierBalanceReportRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<SupplierBalanceReport> query(QueryParams params) throws BusinessException {
        return page(params.getPage(), buildQuery(params));
    }

    @Override
    public PageResponse<SupplierBalanceReport> groupQuery(QueryParams params) throws BusinessException {
        QueryWrapper<SupplierBalanceReport> queryWrapper = buildQuery(params)
                .and(q -> q.ne("unsettled_amount", 0)
                        .or().ne("settled_unpaid_amount", 0)
                        .or().ne("settled_paid_amount", 0)
                        .or().ne("purchase_refund_amount", 0)
                        .or().ne("purchase_return_amount", 0)
                        .or().ne("invoicing_amount", 0)
                        .or().ne("invoiced_amount", 0)
                        .or().ne("paid_invoice_amount", 0)
                        .or().ne("payable_invoice_amount", 0));
        if (CollectionUtils.isEmpty(params.getGroups())) {
            return page(params.getPage(), queryWrapper);
        } else {
            queryWrapper.groupBy(params.getGroups());
            List<String> select = getSelect(params.getGroups());
            queryWrapper.select(String.join(",", select));
            return page(params.getPage(), queryWrapper);
        }
    }

    private PageResponse<SupplierBalanceReport> page(PageParams pageParams, QueryWrapper<SupplierBalanceReport> queryWrapper) {
        if (pageParams != null) {
            Page<SupplierBalanceReport> page = this.repo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<SupplierBalanceReport> list = this.repo.selectList(queryWrapper);
            return new PageResponse<>(Long.parseLong(String.valueOf(list.size())), list);
        }
    }

    private List<String> getSelect(List<String> groups) {
        List<String> select = new ArrayList<>(groups);
        select.add("SUM(total_amount) as total_amount");
        select.add("SUM(unsettled_amount) as unsettled_amount");
        select.add("SUM(settled_unpaid_amount) as settled_unpaid_amount");
        select.add("SUM(settled_paid_amount) as settled_paid_amount");
        select.add("SUM(purchase_refund_amount) as purchase_refund_amount");
        select.add("SUM(purchase_return_amount) as purchase_return_amount");
        select.add("SUM(invoicing_amount) as invoicing_amount");
        select.add("SUM(invoiced_amount) as invoiced_amount");
        select.add("SUM(paid_invoice_amount) as paid_invoice_amount");
        select.add("SUM(payable_invoice_amount) as payable_invoice_amount");
        return select;
    }

    @Override
    public SupplierBalanceReport sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select(String.join(",", getSelect(new ArrayList<>()))));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(SupplierBalanceReport.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<SupplierBalanceReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            SupplierBalanceReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void delete() throws BusinessException {
        repo.delete(new QueryWrapper<SupplierBalanceReport>().apply("1=1"));
    }

    private QueryWrapper<SupplierBalanceReport> buildQuery(QueryParams params) {
        QueryWrapper<SupplierBalanceReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getSupplierId() != null, "supplier_id", params.getSupplierId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getSupplierIds()), "supplier_id", params.getSupplierIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        return queryWrapper;
    }
}
