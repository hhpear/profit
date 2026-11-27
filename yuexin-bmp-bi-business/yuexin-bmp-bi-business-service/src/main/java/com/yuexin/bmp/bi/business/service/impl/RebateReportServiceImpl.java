package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.RebateReportRepo;
import com.yuexin.bmp.bi.common.RebateReport;
import com.yuexin.bmp.bi.service.RebateReportService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 返佣跟踪报表(RebateReport)表服务实现类
 *
 * @author lxf
 * @since 2024-09-04 14:55:43
 */
@Slf4j
@DubboService
public class RebateReportServiceImpl implements RebateReportService, NullHandler {
    @Resource
    private RebateReportRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<RebateReport> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<RebateReport> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<RebateReport> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public PageResponse<RebateReport> queryAgent(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .groupBy("statement_id,settlement_id,platform,ent_code,business_body_id,agent_id,month,charge_type,type")
                .select("MAX(id) AS id,statement_id,settlement_id,platform,ent_code,business_body_id,agent_id,month,charge_type,type," +
                        "sum(income_money) as income_money,sum(agency_money) as agency_money,sum(actual_money) as actual_money," +
                        "MAX(adjustment_reason) as adjustment_reason"));
        return page(mapPage);
    }

    @Override
    public PageResponse<RebateReport> queryEnt(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .groupBy("statement_id,settlement_id,platform,ent_code,month,charge_type,type")
                .select("MAX(id) AS id,statement_id,settlement_id,platform,ent_code,month,charge_type,type"));
        return page(mapPage);
    }

    private PageResponse<RebateReport> page(Page<Map<String, Object>> mapPage) {
        Page<RebateReport> page = new Page<>(mapPage.getCurrent(), mapPage.getSize(), mapPage.getTotal());
        page.setRecords(mapPage.getRecords().stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(RebateReport.class)).collect(Collectors.toList()));
        return PageUtils.convert(page);
    }

    @Override
    public RebateReport get(GetParams params) throws BusinessException {
        if (params.getId() != null) {
            return this.repo.selectById(params.getId());
        }
        if (StringUtils.isNotBlank(params.getInstanceCode())) {
            return this.repo.selectOne(new LambdaQueryWrapper<RebateReport>().eq(RebateReport::getInstanceCode, params.getInstanceCode()));
        }
        throw new BusinessException("id或instanceCode不能同时为空");
    }

    @Override
    public List<RebateReport> multiGet(MultiGetParams params) throws BusinessException {
        if (CollectionUtils.isEmpty(params.getStatementIds()) && CollectionUtils.isEmpty(params.getSettlementIds())) {
            return Collections.emptyList();
        }
        List<RebateReport> list = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(params.getStatementIds())) {
            list.addAll(this.repo.selectList(new LambdaQueryWrapper<RebateReport>()
                    .eq(RebateReport::getDeleted, false)
                    .in(RebateReport::getStatementId, params.getStatementIds())));
        }

        if (CollectionUtils.isNotEmpty(params.getSettlementIds())) {
            list.addAll(this.repo.selectList(new LambdaQueryWrapper<RebateReport>()
                    .eq(RebateReport::getDeleted, false)
                    .in(RebateReport::getSettlementId, params.getSettlementIds())));
        }
        return list;
    }

    @Override
    public void add(AddParams params) throws BusinessException {
        RebateReport rebateReport = BeanUtils.copyProperties(params);
        rebateReport.setId(uidGenerator.generate());
        rebateReport.setCreateTime(new Date());
        rebateReport.setDeleted(false);
        this.repo.insert(rebateReport);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<RebateReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            RebateReport rebateReport = BeanUtils.copyProperties(params.get(i));
            rebateReport.setId(ids.get(i));
            rebateReport.setCreateTime(new Date());
            rebateReport.setDeleted(false);
            list.add(rebateReport);
        });
        this.repo.insertBatch(list);

    }

    @Override
    public void modify(ModifyParams params) throws BusinessException {
        RebateReport rebateReport = BeanUtils.copyProperties(params);
        if (params.getId() != null) {
            this.repo.updateById(rebateReport);
            return;
        }
        if (params.getSettlementId() != null) {
            this.repo.update(rebateReport, new LambdaQueryWrapper<RebateReport>()
                    .eq(RebateReport::getDeleted, false)
                    .eq(RebateReport::getSettlementId, params.getSettlementId())
                    .eq(params.getAgentId() != null, RebateReport::getAgentId, params.getAgentId()));
            return;
        }
        if (CollectionUtils.isNotEmpty(params.getSettlementIds())) {
            this.repo.update(rebateReport, new LambdaQueryWrapper<RebateReport>()
                    .eq(RebateReport::getDeleted, false)
                    .in(RebateReport::getSettlementId, params.getSettlementIds())
                    .eq(params.getAgentId() != null, RebateReport::getAgentId, params.getAgentId()));
            return;
        }
        if (params.getStatementId() != null) {
            this.repo.update(rebateReport, new LambdaQueryWrapper<RebateReport>()
                    .eq(RebateReport::getDeleted, false)
                    .eq(RebateReport::getStatementId, params.getStatementId())
                    .eq(params.getAgentId() != null, RebateReport::getAgentId, params.getAgentId()));
            return;
        }
        if (CollectionUtils.isNotEmpty(params.getStatementIds())) {
            this.repo.update(rebateReport, new LambdaQueryWrapper<RebateReport>()
                    .eq(RebateReport::getDeleted, false)
                    .in(RebateReport::getStatementId, params.getStatementIds())
                    .eq(params.getAgentId() != null, RebateReport::getAgentId, params.getAgentId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustActualMoney(AdjustActualMoneyParams params) throws BusinessException {
        if (params.getStatementId() == null && params.getSettlementId() == null) {
            throw new BusinessException("销售对账ID和销售订单ID不能同时为空");
        }
        List<RebateReport> records = this.repo.selectList(new LambdaQueryWrapper<RebateReport>()
                .eq(RebateReport::getDeleted, false)
                .eq(RebateReport::getAgentId, params.getAgentId())
                .eq(params.getStatementId() != null, RebateReport::getStatementId, params.getStatementId())
                .eq(params.getSettlementId() != null, RebateReport::getSettlementId, params.getSettlementId())
                .last("FOR UPDATE"));
        if (CollectionUtils.isEmpty(records)) {
            throw new BusinessException("返佣记录不存在");
        }
        if (records.stream().anyMatch(record -> RebateReport.State.Approving == record.getState()
                || RebateReport.State.Approved == record.getState())) {
            throw new BusinessException("当前返佣状态已发生变化，请刷新后重试");
        }
        BigDecimal currentMoney = records.stream()
                .map(record -> record.getActualMoney() == null ? record.getAgencyMoney() : record.getActualMoney())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (currentMoney.compareTo(params.getActualMoney()) == 0) {
            throw new BusinessException("调整后的实际支付代理金额不能与当前金额相同");
        }
        String adjustmentReason = StringUtils.trim(params.getAdjustmentReason());
        if (StringUtils.isBlank(adjustmentReason)) {
            throw new BusinessException("调整原因不能为空");
        }
        this.repo.update(new RebateReport()
                        .setActualMoney(BigDecimal.ZERO)
                        .setAdjustmentReason(adjustmentReason),
                new LambdaQueryWrapper<RebateReport>()
                        .in(RebateReport::getId, records.stream().map(RebateReport::getId).collect(Collectors.toList())));
        this.repo.updateById(new RebateReport()
                .setId(records.get(0).getId())
                .setActualMoney(params.getActualMoney())
                .setAdjustmentReason(adjustmentReason));
    }

    @Override
    public void modifyAgent(ModifyAgentParams params) throws BusinessException {
        this.repo.update(new RebateReport().setAgentId(params.getAgentId()).setAgentType(params.getAgentType()),
                new LambdaQueryWrapper<RebateReport>().eq(RebateReport::getRebateId, params.getRebateId())
                        .eq(RebateReport::getAgentLevel, params.getAgentLevel()));
    }

    @Override
    public void apply(ApplyParams params) throws BusinessException {
        this.repo.update(BeanUtils.copyProperties(params).setState(RebateReport.State.Approving).setSubmitTime(new Date()),
                new LambdaQueryWrapper<RebateReport>().in(RebateReport::getId, params.getIds()).eq(RebateReport::getAgentId, params.getAgentId()));
    }

    @Override
    public void approve(ApproveParams params) throws BusinessException {
        this.repo.update(new RebateReport().setState(params.getState()).setApproveTime(new Date()),
                new LambdaQueryWrapper<RebateReport>().eq(RebateReport::getInstanceCode, params.getInstanceCode()));
    }

    @Override
    public int approveAndCount(ApproveParams params) throws BusinessException {
        return this.repo.update(new RebateReport().setState(params.getState()).setApproveTime(new Date()),
                new LambdaQueryWrapper<RebateReport>()
                        .eq(RebateReport::getInstanceCode, params.getInstanceCode())
                        .eq(RebateReport::getDeleted, false)
                        .eq(RebateReport::getState, RebateReport.State.Approving));
    }

    private QueryWrapper<RebateReport> buildQuery(QueryParams params) {
        QueryWrapper<RebateReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getAgentIds()), "agent_id", params.getAgentIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getPlatform()), "platform", params.getPlatform());
        queryWrapper.eq(StringUtils.isNotBlank(params.getEntCode()), "ent_code", params.getEntCode());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getEntCodes()), "ent_code", params.getEntCodes());
        queryWrapper.eq(params.getMonth() != null, "month", params.getMonth());
        queryWrapper.ge(params.getMonthStart() != null, "month", params.getMonthStart());
        queryWrapper.lt(params.getMonthEnd() != null, "month", params.getMonthEnd());
        queryWrapper.eq(StringUtils.isNotBlank(params.getChargeType()), "charge_type", params.getChargeType());
        queryWrapper.isNotNull(params.getIsReceive() != null && params.getIsReceive(), "receive_time");
        queryWrapper.isNull(params.getIsReceive() != null && !params.getIsReceive(), "receive_time");
        queryWrapper.eq(params.getIsRebate() != null && params.getIsRebate(), "state", RebateReport.State.Approved);
        if (params.getIsRebate() != null && !params.getIsRebate()) {
            queryWrapper.and(w -> w.isNull("state").or().ne("state", RebateReport.State.Approved));
        }
        queryWrapper.eq(StringUtils.isNotBlank(params.getSerialNumber()), "serial_number", params.getSerialNumber());
        queryWrapper.eq(StringUtils.isNotBlank(params.getInstanceCode()), "instance_code", params.getInstanceCode());
        queryWrapper.eq(params.getState() != null, "state", params.getState());
        queryWrapper.eq("deleted", false);
        return queryWrapper;
    }
}
