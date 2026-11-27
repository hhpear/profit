package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.CustomerTradingReportRepo;
import com.yuexin.bmp.bi.common.CustomerTradingReport;
import com.yuexin.bmp.bi.service.CustomerTradingReportService;
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
 * 客户交易报表(CustomerTradingReport)表服务实现类
 *
 * @author lxf
 * @since 2024-01-23 10:26:06
 */
@Slf4j
@DubboService
public class CustomerTradingReportServiceImpl implements CustomerTradingReportService, NullHandler {
    @Resource
    private CustomerTradingReportRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<CustomerTradingReport> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<CustomerTradingReport> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<CustomerTradingReport> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>(Long.parseLong(String.valueOf(list.size())), list);
        }
    }

    @Override
    public CustomerTradingReport sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select("SUM(paid_amount) as paidAmount," +
                        "SUM(unpaid_amount) as unpaidAmount," +
                        "SUM(ticket_amount) as ticketAmount," +
                        "SUM(non_ticket_amount) as nonTicketAmount," +
                        "SUM(non_ticket_amount_sum) as nonTicketAmountSum," +
                        "SUM(income_amount) as incomeAmount," +
                        "SUM(income_amount_not) as incomeAmountNot"));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(CustomerTradingReport.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<CustomerTradingReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            CustomerTradingReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void batchDelete(DeleteParams params) throws BusinessException {
        repo.delete(new QueryWrapper<CustomerTradingReport>().eq("month", params.getMonth()));
    }

    private QueryWrapper<CustomerTradingReport> buildQuery(QueryParams params) {
        QueryWrapper<CustomerTradingReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.ge(params.getStartTime() != null, "month", params.getStartTime());
        queryWrapper.le(params.getEndTime() != null, "month", params.getEndTime());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIdList()), "customer_id", params.getCustomerIdList());
        if (params.getNonTicketAmount() != null) {
            queryWrapper.gt(params.getNonTicketAmount(), "non_ticket_amount", 0);
            queryWrapper.eq(!params.getNonTicketAmount(), "non_ticket_amount", 0);
        }
        if (params.getUnpaidAmount() != null) {
            queryWrapper.gt(params.getUnpaidAmount(), "unpaid_amount", 0);
            queryWrapper.eq(!params.getUnpaidAmount(), "unpaid_amount", 0);
        }
        queryWrapper.orderBy(true, false, Arrays.asList("month", "income_amount + income_amount_not"));
        return queryWrapper;
    }
}
