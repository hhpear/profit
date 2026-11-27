package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.ChannelCostDetailRepo;
import com.yuexin.bmp.bi.business.repo.CustomerProfitReportRepo;
import com.yuexin.bmp.bi.business.repo.PlatformProfitDetailRepo;
import com.yuexin.bmp.bi.common.*;
import com.yuexin.bmp.bi.service.CustomerProfitReportService;
import com.yuexin.bmp.main.model.Customer;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 客户利润报表(CustomerProfitReport)表服务实现类
 *
 * @author lxf
 * @since 2024-01-23 10:25:35
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class CustomerProfitReportServiceImpl implements CustomerProfitReportService, NullHandler {

    private final CustomerProfitReportRepo customerProfitReportRepo;
    private final PlatformProfitDetailRepo platformProfitDetailRepo;
    private final ChannelCostDetailRepo channelCostDetailRepo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<CustomerProfitReport> query(QueryParams params) throws BusinessException {
        if (CollectionUtils.isEmpty(params.getGroups())) {
            return page(params.getPage(), buildQuery(params, false));
        } else {
            QueryWrapper<CustomerProfitReport> queryWrapper = buildQuery(params, true);
            queryWrapper.groupBy(params.getGroups());
            queryWrapper.select(String.join(",", getSelect(params.getGroups())));
            return page(params.getPage(), queryWrapper);
        }
    }

    private PageResponse<CustomerProfitReport> page(PageParams pageParams, QueryWrapper<CustomerProfitReport> queryWrapper) {
        if (pageParams != null) {
            Page<CustomerProfitReport> page = customerProfitReportRepo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<CustomerProfitReport> list = customerProfitReportRepo.selectList(queryWrapper);
            return new PageResponse<>((long) list.size(), list);
        }
    }

    private List<String> getSelect(List<String> groups) {
        List<String> selects = new ArrayList<>(groups);
        selects.add("SUM(sms_prepayment) as sms_prepayment");
        selects.add("SUM(sms_prepayment_untax) as sms_prepayment_untax");
        selects.add("SUM(sms_income) as sms_income");
        selects.add("SUM(sms_income_untax) as sms_income_untax");
        selects.add("SUM(project_income) as project_income");
        selects.add("SUM(project_income_untax) as project_income_untax");
        selects.add("SUM(rebate) as rebate");
        selects.add("SUM(rebate_untax) as rebate_untax");
        selects.add("SUM(channel_market_cost) as channel_market_cost");
        selects.add("SUM(channel_market_cost_untax) as channel_market_cost_untax");
        selects.add("SUM(channel_operate_cost) as channel_operate_cost");
        selects.add("SUM(channel_operate_cost_untax) as channel_operate_cost_untax");
        selects.add("SUM(project_cost) as project_cost");
        selects.add("SUM(sms_market_profit) as sms_market_profit");
        selects.add("SUM(sms_market_profit_untax) as sms_market_profit_untax");
        selects.add("SUM(sms_operate_profit) as sms_operate_profit");
        selects.add("SUM(sms_operate_profit_untax) as sms_operate_profit_untax");
        selects.add("SUM(project_profit) as project_profit");
        selects.add("SUM(project_profit_untax) as project_profit_untax");
        selects.add("SUM(fee_count) as fee_count");
        selects.add("SUM(success_count) as success_count");
        return selects;
    }

    @Override
    public CustomerProfitReport sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = customerProfitReportRepo.selectMaps(buildQuery(params, false)
                .select("SUM(sms_prepayment) as smsPrepayment," +
                        "SUM(sms_prepayment_untax) as smsPrepaymentUntax," +
                        "SUM(sms_income) as smsIncome," +
                        "SUM(sms_income_untax) as smsIncomeUntax," +
                        "SUM(project_income) as projectIncome," +
                        "SUM(project_income_untax) as projectIncomeUntax," +
                        "SUM(rebate) as rebate," +
                        "SUM(rebate_untax) as rebateUntax," +
                        "SUM(channel_market_cost) as channelMarketCost," +
                        "SUM(channel_market_cost_untax) as channelMarketCostUntax," +
                        "SUM(channel_operate_cost) as channelOperateCost," +
                        "SUM(channel_operate_cost_untax) as channelOperateCostUntax," +
                        "SUM(project_cost) as projectCost," +
                        "SUM(sms_market_profit) as smsMarketProfit," +
                        "SUM(sms_market_profit_untax) as smsMarketProfitUntax," +
                        "SUM(sms_operate_profit) as smsOperateProfit," +
                        "SUM(sms_operate_profit_untax) as smsOperateProfitUntax," +
                        "SUM(project_profit) as projectProfit," +
                        "SUM(project_profit_untax) as projectProfitUntax," +
                        "SUM(fee_count) as feeCount," +
                        "SUM(success_count) as successCount"));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(CustomerProfitReport.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public List<CustomerProfitReport> sumGroupByCustomer(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = customerProfitReportRepo.selectMaps(buildQuery(params, false)
                .select("SUM(sms_income) as smsIncome," +
                        "SUM(project_income) as projectIncome," +
                        "SUM(sms_market_profit) as smsMarketProfit," +
                        "SUM(sms_operate_profit) as smsOperateProfit," +
                        "customer_id")
                .groupBy("customer_id"));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(CustomerProfitReport.class))
                .collect(Collectors.toList());
    }

    @Override
    public void batchAdd(List<AddParams> params) {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<CustomerProfitReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            CustomerProfitReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        customerProfitReportRepo.insertBatch(list);
    }

    @Override
    public void batchDelete(DeleteParams params) throws BusinessException {
        customerProfitReportRepo.delete(new QueryWrapper<CustomerProfitReport>()
                .eq("cycle", params.getCycle())
                .eq("source", params.getSource())
                .ge("time", params.getStartTime())
                .le("time", params.getEndTime()));
    }

    @Override
    public List<CustomerProfitReport> query() throws BusinessException {
        StatSource source = StatSourceResolver.resolve(Cycle.Day, null, null, new Date());
        LambdaQueryWrapper<CustomerProfitReport> queryWrapper = new LambdaQueryWrapper<CustomerProfitReport>()
                .eq(CustomerProfitReport::getCycle, Cycle.Day)
                .eq(CustomerProfitReport::getSource, source)
                .ge(CustomerProfitReport::getTime, DateUtils.addDays(new Date(), -60))
                .select(CustomerProfitReport::getCustomerId,
                        CustomerProfitReport::getBusinessBodyId,
                        CustomerProfitReport::getSmsIncome,
                        CustomerProfitReport::getTime);
        return customerProfitReportRepo.selectList(queryWrapper);
    }

    @Override
    public void updateIncr(IncrStockParams params) throws BusinessException {
        customerProfitReportRepo.update(new CustomerProfitReport().setIncrStock(Customer.IncrStock.Incr.name()),
                new LambdaUpdateWrapper<CustomerProfitReport>().eq(CustomerProfitReport::getCustomerId, params.getCustomerId())
                        .eq(CustomerProfitReport::getBusinessBodyId, params.getBusinessBodyId())
                        .ge(CustomerProfitReport::getTime, params.getStartTime())
                        .le(params.getEndTime() != null, CustomerProfitReport::getTime, params.getEndTime()));
    }

    @Override
    public void updateLeader(LeaderParams params) throws BusinessException {
        // 更新客户利润表
        customerProfitReportRepo.update(
                new CustomerProfitReport()
                        .setProjectLeaderId(params.getLeaderId()),
                new LambdaUpdateWrapper<CustomerProfitReport>()
                        .in(CustomerProfitReport::getProjectId, params.getProjectIds())
        );

        // 更新平台利润详情表
        platformProfitDetailRepo.update(
                new PlatformProfitDetail()
                        .setProjectLeaderId(params.getLeaderId()),
                new LambdaUpdateWrapper<PlatformProfitDetail>()
                        .in(PlatformProfitDetail::getProjectId, params.getProjectIds())
        );

        // 更新成本表
        channelCostDetailRepo.update(
                new ChannelCostDetail()
                        .setProjectLeaderId(params.getLeaderId()),
                new LambdaUpdateWrapper<ChannelCostDetail>()
                        .in(ChannelCostDetail::getProjectId, params.getProjectIds())
        );
    }

    private QueryWrapper<CustomerProfitReport> buildQuery(QueryParams params, boolean orderBy) {
        StatSource source = StatSourceResolver.resolve(params.getCycle(), params.getSource(), null, params.getStartTime());
        QueryWrapper<CustomerProfitReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.eq(params.getProjectId() != null, "project_id", params.getProjectId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectLeaderIds()), "project_leader_id", params.getProjectLeaderIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectIds()), "project_id", params.getProjectIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getPlatform()), "platform", params.getPlatform());
        queryWrapper.eq(StringUtils.isNotBlank(params.getEntCode()), "ent_code", params.getEntCode());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getEntCodes()), "ent_code", params.getEntCodes());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getPlatformEntCodes()), "CONCAT(platform, ent_code)", params.getPlatformEntCodes());
        queryWrapper.eq(params.getCycle() != null, "cycle", params.getCycle());
        queryWrapper.eq(source != null, "source", source);
        queryWrapper.ge(params.getStartTime() != null, "time", params.getStartTime());
        queryWrapper.lt(params.getEndTime() != null, "time", params.getEndTime());
        if (orderBy) {
            if (params.getMarketIndex() != null) {
                if (params.getMarketIndex()) {
                    queryWrapper.apply(params.getProfit() != null && params.getProfit(), "(SUM(sms_market_profit + project_profit)) >= 0");
                    queryWrapper.apply(params.getProfit() != null && !params.getProfit(), "(SUM(sms_market_profit + project_profit)) < 0");
                } else {
                    queryWrapper.apply(params.getProfit() != null && params.getProfit(), "(SUM(sms_operate_profit + project_profit)) >= 0");
                    queryWrapper.apply(params.getProfit() != null && !params.getProfit(), "(SUM(sms_operate_profit + project_profit)) < 0");
                }
            }
            if (params.getProfitIsAsc() != null && params.getMarketIndex() != null) {
                if (params.getMarketIndex()) {
                    queryWrapper.orderBy(true, params.getProfitIsAsc(), "SUM(sms_market_profit + project_profit)");
                } else {
                    queryWrapper.orderBy(true, params.getProfitIsAsc(), "SUM(sms_operate_profit + project_profit)");
                }
            }
        } else {
            if (params.getMarketIndex() != null) {
                if (params.getMarketIndex()) {
                    queryWrapper.apply(params.getProfit() != null && params.getProfit(), "(sms_market_profit + project_profit) >= 0");
                    queryWrapper.apply(params.getProfit() != null && !params.getProfit(), "(sms_market_profit + project_profit) < 0");
                } else {
                    queryWrapper.apply(params.getProfit() != null && params.getProfit(), "(sms_operate_profit + project_profit) >= 0");
                    queryWrapper.apply(params.getProfit() != null && !params.getProfit(), "(sms_operate_profit + project_profit) < 0");
                }
            }
            if (params.getProfitIsAsc() != null && params.getMarketIndex() != null) {
                if (params.getMarketIndex()) {
                    queryWrapper.orderBy(true, params.getProfitIsAsc(), "sms_market_profit + project_profit");
                } else {
                    queryWrapper.orderBy(true, params.getProfitIsAsc(), "sms_operate_profit + project_profit");
                }
            }
        }

        queryWrapper.in(CollectionUtils.isNotEmpty(params.getNotTestCustomerIds()), "customer_id", params.getNotTestCustomerIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getIncrStock()), "incr_stock", params.getIncrStock());
        return queryWrapper;
    }
}
