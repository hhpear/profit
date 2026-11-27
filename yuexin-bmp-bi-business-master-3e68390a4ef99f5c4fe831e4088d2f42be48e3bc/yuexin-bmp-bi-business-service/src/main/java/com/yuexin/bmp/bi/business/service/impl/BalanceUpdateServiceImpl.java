package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.ChannelBalanceRepo;
import com.yuexin.bmp.bi.common.ChannelBalance;
import com.yuexin.bmp.bi.common.ChannelChangeDetail;
import com.yuexin.bmp.bi.common.ChannelRechargeDetail;
import com.yuexin.bmp.bi.common.NetType;
import com.yuexin.bmp.bi.service.BalanceUpdateService;
import com.yuexin.bmp.bi.service.ChannelChangeDetailService;
import com.yuexin.bmp.bi.service.ChannelRechargeDetailService;
import com.yuexin.bmp.srm.business.service.ChannelQuoteService;
import com.yuexin.bmp.srm.business.service.ChannelService;
import com.yuexin.bmp.srm.business.service.PurchaseOrderDetailService;
import com.yuexin.bmp.srm.business.service.PurchaseStatementDetailService;
import com.yuexin.bmp.srm.model.*;
import com.yuexin.business.Network;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@DubboService
@RequiredArgsConstructor
public class BalanceUpdateServiceImpl implements BalanceUpdateService, NullHandler {

    private final ChannelBalanceRepo channelBalanceRepo;

    @DubboReference
    private ChannelService channelService;
    @DubboReference
    private PurchaseStatementDetailService purchaseStatementDetailService;
    @DubboReference
    private PurchaseOrderDetailService purchaseOrderDetailService;
    @DubboReference
    private ChannelRechargeDetailService channelRechargeDetailService;
    @DubboReference
    private ChannelChangeDetailService channelChangeDetailService;
    @DubboReference
    private ChannelQuoteService channelQuoteService;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public void update(UpdateParams params) throws BusinessException {
        Channel channel = channelService.get(new ChannelService.GetParams().setCode(params.getChannelCode()));
        if (channel == null) {
            throw new BusinessException("未获取到通道信息：" + params.getChannelCode());
        }
        ChannelBalance balance = channelBalanceRepo.selectOne(new LambdaQueryWrapper<ChannelBalance>()
                .eq(ChannelBalance::getCreateTime, DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH))
                .eq(ChannelBalance::getChannelCode, params.getChannelCode()));
        if (balance != null) {
            handleBalance(channel, balance);
            channelBalanceRepo.updateById(balance.setUpdateTime(new Date()));
        } else {
            balance = new ChannelBalance()
                    .setId(uidGenerator.generate())
                    .setUpdateTime(new Date())
                    .setChannelId(channel.getId())
                    .setChannelCode(channel.getCode())
                    .setChannelName(channel.getName())
                    .setSupplierId(channel.getSupplierId())
                    .setBusinessBodyId(channel.getBusinessBodyId())
                    .setSpNumber(channel.getSpNumber())
                    .setLandProvince(channel.getLandProvince())
                    .setLandCity(channel.getLandCity())
                    .setAgentName(channel.getAgentName())
                    .setCreateTime(DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH))
                    .setAllAvgCount(0L)
                    .setPoAvgCount(0L)
                    .setAllPreDays(0)
                    .setPoPreDays(0);
            handleBalance(channel, balance);
            channelBalanceRepo.insert(balance);
        }
    }

    private void handleBalance(Channel channel, ChannelBalance balance) {
        Map<String, PurchaseOrderSum> preOrderSumMap = queryOrderSum(channel.getCode(), PurchaseOrder.PurchaseType.Prepayment);
        Map<String, PurchaseStatementSum> billSumMap = queryStatementSum(channel.getCode());
        Map<String, ChannelRechargeDetail> rechargeSumMap = queryRechargeSum();
        Map<String, ChannelChangeDetail> changeSumMap = queryChangeSum();
        count(channel, balance, getRechargeSum(rechargeSumMap, channel.getCode(), NetType.All),
                getPreOrderSum(preOrderSumMap, PurchaseOrder.RechargeState.ZT),
                getRechargeSum(rechargeSumMap, channel.getCode(), NetType.Po),
                getStatementSum(billSumMap, NetType.All), getStatementSum(billSumMap, NetType.Po),
                getChangeSum(changeSumMap, channel.getCode(), NetType.All),
                getChangeSum(changeSumMap, channel.getCode(), NetType.Po));
    }

    private void count(Channel channel, ChannelBalance balance, ChannelRechargeDetail allRecharge, PurchaseOrderSum preZtOrderSum,
                       ChannelRechargeDetail poRecharge, PurchaseStatementSum allBillSum, PurchaseStatementSum poBillSum,
                       ChannelChangeDetail allChange, ChannelChangeDetail poChange) {
        try {
            // 到账金额
            balance.setDzAmount(nonNull(allRecharge.getAmount()));
            // 在途金额
            balance.setZtAmount(nonNull(preZtOrderSum.getAmount()));
            // 账单金额
            balance.setZdAmount(nonNull(allBillSum.getAmount()));
            // 全网剩余金额
            balance.setAllAmount(balance.getDzAmount().subtract(balance.getZdAmount()).add(nonNull(allChange.getAmount())));
            // 全网实际剩余金额
            balance.setAllActualAmount(nonNull(allRecharge.getActualPrice())
                    .subtract(nonNull(allBillSum.getActualAmount()))
                    .add(nonNull(allChange.getAmount())));
            if (channel.getPoBalance() && (channel.getEffectiveDate() == null || channel.getEffectiveDate().before(new Date()))) {
                // 省网实际剩余金额
                balance.setPoActualAmount(nonNull(poRecharge.getActualPrice())
                        .subtract(nonNull(poBillSum.getActualAmount()))
                        .add(nonNull(poChange.getAmount())));
            } else {
                // 省网实际剩余金额
                balance.setPoActualAmount(BigDecimal.ZERO);
            }
            // 订单条数
            balance.setDzCount(nonNull(allRecharge.getCount()));
            // 账单条数
            balance.setZdCount(nonNull(allBillSum.getCount()) + nonNull(poBillSum.getCount()));
            // 全网剩余条数
            ChannelQuote.Quote quote = getCmccQuote(channel.getId());
            if (quote != null && quote.getPurchasePrice() != null && quote.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
                balance.setAllCount(balance.getAllAmount().divide(quote.getPurchasePrice(), 0, RoundingMode.HALF_UP).longValue());
            } else {
                balance.setAllCount(nonNull(allRecharge.getCount()) - nonNull(allBillSum.getCount()) + nonNull(allChange.getCount()));
            }
            // 省网剩余条数
            balance.setPoCount(nonNull(poRecharge.getCount()) - nonNull(poBillSum.getCount()) + nonNull(poChange.getCount()));
            // 全网预计可发送天数
            if (balance.getAllAvgCount() > 0) {
                balance.setAllPreDays((int) (balance.getAllCount() / balance.getAllAvgCount()));
            }
            // 省网预计可发送天数
            if (balance.getPoAvgCount() > 0) {
                balance.setPoPreDays((int) (balance.getPoCount() / balance.getPoAvgCount()));
            }
        } catch (Exception e) {
            log.error("计算余额异常", e);
        }
    }

    private PurchaseOrderSum getPreOrderSum(Map<String, PurchaseOrderSum> preOrderSumMap, PurchaseOrder.RechargeState type) {
        PurchaseOrderSum preDzOrderSum = preOrderSumMap.get(type.name());
        if (preDzOrderSum != null) {
            return preDzOrderSum;
        }
        return new PurchaseOrderSum();
    }

    private PurchaseOrderSum getFanOrderSum(Map<String, PurchaseOrderSum> fanOrderSumMap) {
        PurchaseOrderSum fanOrderSum = fanOrderSumMap.get(PurchaseOrder.RechargeState.DZ.name());
        if (fanOrderSum != null) {
            return fanOrderSum;
        }
        return new PurchaseOrderSum();
    }

    private PurchaseStatementSum getStatementSum(Map<String, PurchaseStatementSum> statementSumMap, NetType netType) {
        PurchaseStatementSum statementSum = statementSumMap.get(netType.name());
        if (statementSum != null) {
            return statementSum;
        }
        return new PurchaseStatementSum();
    }

    private ChannelRechargeDetail getRechargeSum(Map<String, ChannelRechargeDetail> rechargeSumMap, String channelCode, NetType netType) {
        ChannelRechargeDetail rechargeSum = rechargeSumMap.get(channelCode + netType.name());
        if (rechargeSum != null) {
            return rechargeSum;
        }
        return new ChannelRechargeDetail();
    }

    private ChannelChangeDetail getChangeSum(Map<String, ChannelChangeDetail> changeSumMap, String channelCode, NetType netType) {
        ChannelChangeDetail changeSum = changeSumMap.get(channelCode + netType.name());
        if (changeSum != null) {
            return changeSum;
        }
        return new ChannelChangeDetail();
    }

    private Map<String, PurchaseStatementSum> queryStatementSum(String channelCode) {
        try {
            return purchaseStatementDetailService.querySum(new PurchaseStatementDetailService.QueryParams().setChannelCode(channelCode))
                    .stream().collect(Collectors.toMap(PurchaseStatementSum::getNetType, Function.identity()));
        } catch (BusinessException e) {
            log.error("查询采购对账单统计信息失败！", e);
        }
        return new HashMap<>();
    }

    private Map<String, PurchaseOrderSum> queryOrderSum(String channelCode, PurchaseOrder.PurchaseType type) {
        try {
            return purchaseOrderDetailService.querySum(new PurchaseOrderDetailService.QueryParams()
                            .setChannelCode(channelCode).setType(type).setState(PurchaseOrder.State.Normal))
                    .stream().collect(Collectors.toMap(PurchaseOrderSum::getRechargeState, Function.identity()));
        } catch (BusinessException e) {
            log.error("查询采购订单统计信息失败！", e);
        }
        return new HashMap<>();
    }

    private Map<String, ChannelRechargeDetail> queryRechargeSum() {
        try {
            return channelRechargeDetailService.querySum().stream()
                    .collect(Collectors.toMap(c -> c.getChannelCode() + c.getNetType().name(), Function.identity()));
        } catch (BusinessException e) {
            log.error("查询充值记录信息失败！", e);
        }
        return new HashMap<>();
    }

    private Map<String, ChannelChangeDetail> queryChangeSum() {
        try {
            return channelChangeDetailService.querySum().stream()
                    .collect(Collectors.toMap(c -> c.getChannelCode() + c.getNetType().name(), Function.identity()));
        } catch (BusinessException e) {
            log.error("查询其他变更信息失败！", e);
        }
        return new HashMap<>();
    }

    private ChannelQuote.Quote getCmccQuote(Long channelId) throws BusinessException {
        List<ChannelQuote> quotes = channelQuoteService.query(new ChannelQuoteService.QueryParams().setChannelId(channelId)).getRecords();
        if (CollectionUtils.isEmpty(quotes)) {
            return null;
        }
        // 按日期倒序 取最新的
        ChannelQuote channelQuote = quotes.stream()
                .sorted((Comparator.comparing(ChannelQuote::getCreateTime).reversed()))
                .filter(quote -> quote.getEffectiveDate().before(new Date()))
                .findFirst().orElse(null);
        if (channelQuote == null || channelQuote.getQuotes() == null) {
            return null;
        }
        return channelQuote.getQuotes().stream().filter(q -> "-1".equals(q.getProvince()) && Network.CMCC == q.getNetwork())
                .findFirst().orElse(null);
    }
}


