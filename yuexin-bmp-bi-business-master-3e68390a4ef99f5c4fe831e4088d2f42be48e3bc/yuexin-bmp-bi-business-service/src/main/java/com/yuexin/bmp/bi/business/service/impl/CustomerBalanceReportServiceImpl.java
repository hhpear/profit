package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.CustomerBalanceReportRepo;
import com.yuexin.bmp.bi.common.CustomerBalanceReport;
import com.yuexin.bmp.bi.service.CustomerBalanceReportService;
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
 * 客户交易报表(CustomerBalanceReport)表服务实现类
 *
 * @author lxf
 * @since 2024-01-23 10:26:06
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class CustomerBalanceReportServiceImpl implements CustomerBalanceReportService, NullHandler {

    private final CustomerBalanceReportRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<CustomerBalanceReport> query(QueryParams params) throws BusinessException {
        return page(params.getPage(), buildQuery(params));
    }

    @Override
    public PageResponse<CustomerBalanceReport> groupQuery(QueryParams params) throws BusinessException {
        QueryWrapper<CustomerBalanceReport> queryWrapper = buildQuery(params)
                .and(q -> q.ne("bill_unsettled_amount", 0)
                        .or().ne("billed_unsettled_amount", 0)
                        .or().ne("settled_invoice_amount", 0)
                        .or().ne("settled_invoiced_amount", 0)
                        .or().ne("sales_return_amount", 0)
                        .or().ne("sales_refund_amount", 0)
                        .or().ne("paid_amount", 0)
                        .or().ne("suspected_paid_amount", 0));
        if (CollectionUtils.isEmpty(params.getGroups())) {
            return page(params.getPage(), queryWrapper);
        } else {
            queryWrapper.groupBy(params.getGroups());
            List<String> select = getSelect(params.getGroups());
            queryWrapper.select(String.join(",", select));
            return page(params.getPage(), queryWrapper);
        }
    }

    private PageResponse<CustomerBalanceReport> page(PageParams pageParams, QueryWrapper<CustomerBalanceReport> queryWrapper) {
        if (pageParams != null) {
            Page<CustomerBalanceReport> page = this.repo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<CustomerBalanceReport> list = this.repo.selectList(queryWrapper);
            return new PageResponse<>(Long.parseLong(String.valueOf(list.size())), list);
        }
    }

    private List<String> getSelect(List<String> groups) {
        List<String> select = new ArrayList<>(groups);
        select.add("SUM(total_amount) as total_amount");
        select.add("SUM(unsettled_amount) as unsettled_amount");
        select.add("SUM(bill_unsettled_amount) as bill_unsettled_amount");
        select.add("SUM(billed_unsettled_amount) as billed_unsettled_amount");
        select.add("SUM(settled_invoice_amount) as settled_invoice_amount");
        select.add("SUM(settled_invoiced_amount) as settled_invoiced_amount");
        select.add("SUM(sales_return_amount) as sales_return_amount");
        select.add("SUM(sales_refund_amount) as sales_refund_amount");
        select.add("SUM(paid_amount) as paid_amount");
        select.add("SUM(suspected_paid_amount) as suspected_paid_amount");
        select.add("SUM(settled_unpaid_with_amount) as settled_unpaid_with_amount");
        select.add("SUM(CASE WHEN settled_unpaid_with_amount > 0 THEN settled_unpaid_with_amount ELSE 0 END) as rec_settled_unpaid_with_amount");
        select.add("SUM(CASE WHEN settled_unpaid_with_amount < 0 THEN settled_unpaid_with_amount ELSE 0 END) as pre_settled_unpaid_with_amount");
        select.add("SUM(settled_unpaid_without_amount) as settled_unpaid_without_amount");
        select.add("SUM(CASE WHEN settled_unpaid_without_amount > 0 THEN settled_unpaid_without_amount ELSE 0 END) as rec_settled_unpaid_without_amount");
        select.add("SUM(CASE WHEN settled_unpaid_without_amount < 0 THEN settled_unpaid_without_amount ELSE 0 END) as pre_settled_unpaid_without_amount");
        select.add("SUM(cumulative_unpaid_with_amount) as cumulative_unpaid_with_amount");
        select.add("SUM(cumulative_unpaid_without_amount) as cumulative_unpaid_without_amount");
        return select;
    }

    @Override
    public StatVO sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select(String.join(",", getSelect(new ArrayList<>()))));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(StatVO.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<CustomerBalanceReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            CustomerBalanceReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void delete() throws BusinessException {
        repo.delete(new QueryWrapper<CustomerBalanceReport>().apply("1=1"));
    }

    private QueryWrapper<CustomerBalanceReport> buildQuery(QueryParams params) {
        QueryWrapper<CustomerBalanceReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        return queryWrapper;
    }
}
