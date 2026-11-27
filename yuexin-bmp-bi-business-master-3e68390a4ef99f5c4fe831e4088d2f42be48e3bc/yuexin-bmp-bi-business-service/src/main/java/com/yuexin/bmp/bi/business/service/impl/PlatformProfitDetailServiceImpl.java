package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.PlatformProfitDetailRepo;
import com.yuexin.bmp.bi.common.*;
import com.yuexin.bmp.bi.service.PlatformProfitDetailService;
import com.yuexin.bmp.main.model.Customer;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 平台利润明细(PlatformProfitDetail)表服务实现类
 *
 * @author lxf
 * @since 2024-01-31 18:48:34
 */
@Slf4j
@DubboService
public class PlatformProfitDetailServiceImpl implements PlatformProfitDetailService, NullHandler {
    @Resource
    private PlatformProfitDetailRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<PlatformProfitDetail> query(QueryParams params) throws BusinessException {
        return page(params.getPage(), buildQuery(params));
    }

    private PageResponse<PlatformProfitDetail> page(PageParams pageParams, QueryWrapper<PlatformProfitDetail> queryWrapper) {
        if (pageParams != null) {
            Page<PlatformProfitDetail> page = this.repo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<PlatformProfitDetail> list = this.repo.selectList(queryWrapper);
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public PageResponse<PlatformProfitDetail> querySmsDay(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .groupBy("platform,ent_code,charge_type,rebate_id,time,source")
                .select("MAX(id) AS id,platform,ent_code,charge_type,sum(submit_count) AS submit_count," +
                        "sum(fee_count) AS fee_count,sum(success_count) AS success_count," +
                        "sum(amount) AS amount,sum(amount_untax) AS amount_untax," +
                        "rebate_id,time,source"));
        Page<PlatformProfitDetail> page = new Page<>(mapPage.getCurrent(), mapPage.getSize(), mapPage.getTotal());
        page.setRecords(mapPage.getRecords().stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PlatformProfitDetail.class)).collect(Collectors.toList()));
        return PageUtils.convert(page);
    }

    @Override
    public PageResponse<PlatformProfitDetail> querySmsMonth(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .groupBy("platform,ent_code,charge_type,rebate_id,time,state,source")
                .select("MAX(id) AS id,platform,ent_code,charge_type,sum(submit_count) AS submit_count," +
                        "sum(fee_count) AS fee_count,sum(success_count) AS success_count," +
                        "sum(amount) AS amount,sum(amount_untax) AS amount_untax," +
                        "rebate_id,time,state,source"));
        Page<PlatformProfitDetail> page = new Page<>(mapPage.getCurrent(), mapPage.getSize(), mapPage.getTotal());
        page.setRecords(mapPage.getRecords().stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PlatformProfitDetail.class)).collect(Collectors.toList()));
        return PageUtils.convert(page);
    }

    @Override
    public PageResponse<PlatformProfitDetail> queryRebateDay(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .groupBy("platform,ent_code,account_number,charge_type,rebate_id,time,source")
                .select("MAX(id) AS id,platform,ent_code,account_number,charge_type,sum(submit_count) AS submit_count," +
                        "sum(fee_count) AS fee_count,sum(success_count) AS success_count," +
                        "rebate_id,sum(rebate_count) AS rebate_count,sum(rebate_count_untax) AS rebate_count_untax," +
                        "time,source"));
        Page<PlatformProfitDetail> page = new Page<>(mapPage.getCurrent(), mapPage.getSize(), mapPage.getTotal());
        page.setRecords(mapPage.getRecords().stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PlatformProfitDetail.class)).collect(Collectors.toList()));
        return PageUtils.convert(page);
    }

    @Override
    public PageResponse<PlatformProfitDetail> queryRebateMonth(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .groupBy("platform,ent_code,account_number,charge_type,rebate_id,time,state,source")
                .select("MAX(id) AS id,platform,ent_code,account_number,charge_type,sum(submit_count) AS submit_count," +
                        "sum(fee_count) AS fee_count,sum(success_count) AS success_count," +
                        "rebate_id,sum(rebate_count) AS rebate_count,sum(rebate_count_untax) AS rebate_count_untax," +
                        "time,state,source"));
        Page<PlatformProfitDetail> page = new Page<>(mapPage.getCurrent(), mapPage.getSize(), mapPage.getTotal());
        page.setRecords(mapPage.getRecords().stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PlatformProfitDetail.class)).collect(Collectors.toList()));
        return PageUtils.convert(page);
    }

    @Override
    public PlatformProfitDetail sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select("SUM(submit_count) AS submit_count",
                        "SUM(fee_count) AS fee_count",
                        "SUM(success_count) AS success_count"));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PlatformProfitDetail.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public PageResponse<PlatformProfitDetail> groupQuery(QueryParams params) throws BusinessException {
        if (CollectionUtils.isEmpty(params.getGroups())) {
            return page(params.getPage(), buildQuery(params));
        } else {
            QueryWrapper<PlatformProfitDetail> queryWrapper = buildQuery(params);
            queryWrapper.groupBy(params.getGroups());
            queryWrapper.select(String.join(",", getSelect(params.getGroups())));
            return page(params.getPage(), queryWrapper);
        }
    }

    @Override
    public List<PlatformProfitDetail> stat(QueryParams params) throws BusinessException {
        QueryWrapper<PlatformProfitDetail> queryWrapper = buildQuery(params).select(String.join(",", getSelect(params.getGroups())));
        if (CollectionUtils.isNotEmpty(params.getGroups())) {
            queryWrapper.groupBy(params.getGroups());
        }
        List<Map<String, Object>> maps = this.repo.selectMaps(queryWrapper);
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PlatformProfitDetail.class))
                .collect(Collectors.toList());
    }

    private List<String> getSelect(List<String> groups) {
        List<String> selects = new ArrayList<>();
        if (groups != null) {
            selects.addAll(groups);
        }
        selects.add("SUM(submit_count) AS submit_count");
        selects.add("SUM(last_submit_count) AS last_submit_count");
        selects.add("SUM(fee_count) AS fee_count");
        selects.add("SUM(last_fee_count) AS last_fee_count");
        selects.add("SUM(success_count) AS success_count");
        selects.add("SUM(last_success_count) AS last_success_count");
        selects.add("SUM(amount) AS amount");
        selects.add("SUM(amount_untax) AS amount_untax");
        selects.add("SUM(last_amount) AS last_amount");
        selects.add("SUM(last_amount_untax) AS last_amount_untax");
        selects.add("SUM(rebate_count) AS rebate_count");
        selects.add("SUM(rebate_count_untax) AS rebate_count_untax");
        selects.add("SUM(channel_market_cost) AS channel_market_cost");
        selects.add("SUM(channel_market_cost_untax) AS channel_market_cost_untax");
        selects.add("SUM(sms_market_profit) AS sms_market_profit");
        selects.add("SUM(sms_market_profit_untax) AS sms_market_profit_untax");
        selects.add("SUM(last_sms_market_profit) AS last_sms_market_profit");
        selects.add("SUM(last_sms_market_profit_untax) AS last_sms_market_profit_untax");
        selects.add("SUM(sms_market_with_profit) AS sms_market_with_profit");
        selects.add("SUM(sms_market_with_profit_untax) AS sms_market_with_profit_untax");
        selects.add("SUM(last_sms_market_with_profit) AS last_sms_market_with_profit");
        selects.add("SUM(last_sms_market_with_profit_untax) AS last_sms_market_with_profit_untax");
        selects.add("SUM(channel_operate_cost) AS channel_operate_cost");
        selects.add("SUM(channel_operate_cost_untax) AS channel_operate_cost_untax");
        selects.add("SUM(sms_operate_profit) AS sms_operate_profit");
        selects.add("SUM(sms_operate_profit_untax) AS sms_operate_profit_untax");
        selects.add("SUM(last_sms_operate_profit) AS last_sms_operate_profit");
        selects.add("SUM(last_sms_operate_profit_untax) AS last_sms_operate_profit_untax");
        selects.add("SUM(sms_operate_with_profit) AS sms_operate_with_profit");
        selects.add("SUM(sms_operate_with_profit_untax) AS sms_operate_with_profit_untax");
        selects.add("SUM(last_sms_operate_with_profit) AS last_sms_operate_with_profit");
        selects.add("SUM(last_sms_operate_with_profit_untax) AS last_sms_operate_with_profit_untax");
        return selects;
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<PlatformProfitDetail> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            PlatformProfitDetail report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void batchUpdate(List<PlatformProfitDetail> params) throws BusinessException {
        Lists.partition(params, 500).forEach(list -> this.repo.insertBatch(list));
    }

    @Override
    public void batchDelete(DeleteParams params) throws BusinessException {
        repo.delete(new QueryWrapper<PlatformProfitDetail>()
                .eq("cycle", params.getCycle())
                .eq("source", params.getSource())
                .ge("time", params.getStartTime())
                .le("time", params.getEndTime()));
    }

    @Override
    public void updateIncr(IncrStockParams params) throws BusinessException {
        repo.update(new PlatformProfitDetail().setIncrStock(Customer.IncrStock.Incr.name()),
                new LambdaUpdateWrapper<PlatformProfitDetail>().eq(PlatformProfitDetail::getCustomerId, params.getCustomerId())
                        .eq(PlatformProfitDetail::getBusinessBodyId, params.getBusinessBodyId())
                        .ge(PlatformProfitDetail::getTime, params.getStartTime())
                        .le(params.getEndTime() != null, PlatformProfitDetail::getTime, params.getEndTime()));
    }


    private QueryWrapper<PlatformProfitDetail> buildQuery(QueryParams params) {
        StatSource source = StatSourceResolver.resolve(params.getCycle(), params.getSource(), params.getTime(), params.getStartTime());
        QueryWrapper<PlatformProfitDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.eq(params.getProjectId() != null, "project_id", params.getProjectId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectIds()), "project_id", params.getProjectIds());
        queryWrapper.eq(params.getProjectLeaderId() != null, "project_leader_id", params.getProjectLeaderId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectLeaderIds()), "project_leader_id", params.getProjectLeaderIds());
        queryWrapper.eq(params.getCycle() != null, "cycle", params.getCycle());
        queryWrapper.eq(source != null, "source", source);
        queryWrapper.eq(params.getTime() != null, "time", params.getTime());
        queryWrapper.ge(params.getStartTime() != null, "time", params.getStartTime());
        queryWrapper.lt(params.getEndTime() != null, "time", params.getEndTime());
        queryWrapper.eq(StringUtils.isNotBlank(params.getPlatform()), "platform", params.getPlatform());
        queryWrapper.eq(StringUtils.isNotBlank(params.getEntCode()), "ent_code", params.getEntCode());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getEntCodes()), "ent_code", params.getEntCodes());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getPlatformEntCodes()), "CONCAT(platform, ent_code)", params.getPlatformEntCodes());
        queryWrapper.eq(StringUtils.isNotBlank(params.getAccountNumber()), "account_number", params.getAccountNumber());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getAccountNumbers()), "account_number", params.getAccountNumbers());
        applyProfitConditions(queryWrapper, params);
        applyBusinessConditions(queryWrapper, params);
        queryWrapper.eq(StringUtils.isNotBlank(params.getIncrStock()), "incr_stock", params.getIncrStock());
        return queryWrapper;
    }

    private void applyProfitConditions(QueryWrapper<PlatformProfitDetail> queryWrapper, QueryParams params) {
        if (params.getProfitEnum() == null) {
            return;
        }
        if (ProfitEnum.SmsProfit == params.getProfitEnum()) {
            if (params.getMarketIndex()) {
                queryWrapper.lt("sms_market_with_profit", 0);
            } else {
                queryWrapper.lt("sms_operate_with_profit", 0);
            }
            return;
        }
        if (ProfitEnum.RebateProfit == params.getProfitEnum()) {
            if (params.getMarketIndex()) {
                queryWrapper.lt("sms_market_profit", 0);
            } else {
                queryWrapper.lt("sms_operate_profit", 0);
            }
        }
    }

    private void applyBusinessConditions(QueryWrapper<PlatformProfitDetail> queryWrapper, QueryParams params) {
        if (params.getBusinessEnum() == null) {
            return;
        }
        if (BusinessEnum.SubmitCount == params.getBusinessEnum()) {
            queryWrapper.apply("submit_count < last_submit_count");
            return;
        }
        if (BusinessEnum.FeeCount == params.getBusinessEnum()) {
            queryWrapper.apply("fee_count < last_fee_count");
            return;
        }
        if (BusinessEnum.ProfitRate == params.getBusinessEnum()) {
            if (params.getMarketIndex()) {
                queryWrapper.apply("sms_market_with_rate < last_sms_market_with_rate");
            } else {
                queryWrapper.apply("sms_operate_with_rate < last_sms_operate_with_rate");
            }
            return;
        }
        if (BusinessEnum.RebateProfitRate == params.getBusinessEnum()) {
            if (params.getMarketIndex()) {
                queryWrapper.apply("sms_market_rate < last_sms_market_rate");
            } else {
                queryWrapper.apply("sms_operate_rate < last_sms_operate_rate");
            }
        }
    }
}
