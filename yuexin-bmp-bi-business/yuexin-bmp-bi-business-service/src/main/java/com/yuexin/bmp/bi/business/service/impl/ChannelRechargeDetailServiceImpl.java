package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.ChannelRechargeDetailRepo;
import com.yuexin.bmp.bi.common.ChannelRechargeDetail;
import com.yuexin.bmp.bi.common.SumResult;
import com.yuexin.bmp.bi.service.ChannelRechargeDetailService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author lxf
 * @since 2024-11-20 13:45:08
 */
@Slf4j
@DubboService
public class ChannelRechargeDetailServiceImpl implements ChannelRechargeDetailService, NullHandler {
    @Resource
    private ChannelRechargeDetailRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<ChannelRechargeDetail> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<ChannelRechargeDetail> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<ChannelRechargeDetail> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public List<ChannelRechargeDetail> queryMonthGroup(QueryParams params) throws BusinessException {
        QueryWrapper<ChannelRechargeDetail> queryWrapper = buildQuery(params)
                .select("channel_code",
                        "SUM(amount) as amount",
                        "sum(count) as count",
                        "DATE_FORMAT(time,'%Y-%m') as month");
        queryWrapper.groupBy("channel_code,month");
        List<Map<String, Object>> maps = this.repo.selectMaps(queryWrapper);
        return maps.stream().map(map -> {
            if (map != null) {
                try {
                    return new ChannelRechargeDetail()
                            .setChannelCode(map.get("channel_code").toString())
                            .setTime(DateUtils.parseDate(map.get("month").toString(), "yyyy-MM"))
                            .setAmount(Objects.nonNull(map.get("amount")) ? new BigDecimal(map.get("amount").toString()) : BigDecimal.ZERO)
                            .setCount(Objects.nonNull(map.get("count")) ? Long.parseLong(map.get("count").toString()) : 0);
                } catch (ParseException e) {
                    log.error("时间转换错误", e);
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public List<ChannelRechargeDetail> queryDayGroup(QueryParams params) throws BusinessException {
        List<ChannelRechargeDetail> list = this.repo.selectList(buildQuery(params));
        return list.stream().collect(Collectors.groupingBy(r -> DateUtils.truncate(r.getTime(), Calendar.DAY_OF_MONTH)))
                .entrySet().stream().map(entry -> {
                    List<ChannelRechargeDetail> values = entry.getValue();
                    BigDecimal amount = values.stream().map(ChannelRechargeDetail::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    Long count = values.stream().map(ChannelRechargeDetail::getCount).filter(Objects::nonNull).reduce(0L, Long::sum);
                    Set<BigDecimal> prices = values.stream().map(ChannelRechargeDetail::getPrice).collect(Collectors.toSet());
                    Set<BigDecimal> actualPrices = values.stream().map(ChannelRechargeDetail::getActualPrice).collect(Collectors.toSet());
                    ChannelRechargeDetail detail = values.stream().max(Comparator.comparing(ChannelRechargeDetail::getTime)).orElse(new ChannelRechargeDetail());
                    return new ChannelRechargeDetail().setTime(entry.getKey())
                            .setAmount(amount)
                            .setCount(count)
                            .setPrice(prices.size() == 1 ? prices.iterator().next() : null)
                            .setActualPrice(actualPrices.size() == 1 ? actualPrices.iterator().next() : null)
                            .setRemark(detail.getRemark());
                }).collect(Collectors.toList());
    }

    @Override
    public List<ChannelRechargeDetail> querySum() throws BusinessException {
        return this.repo.selectList(new QueryWrapper<ChannelRechargeDetail>()
                .groupBy("channel_code,net_type")
                .select("channel_code,net_type,SUM(amount) as amount,SUM(count) as count,SUM(actual_price * count) AS actual_price"));
    }

    @Override
    public SumResult sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select("SUM(amount) as amount,SUM(count) as count"));
        return maps.stream().map(map -> new JSONObject(nonNull(map))
                .toJavaObject(SumResult.class)).findFirst().orElse(new SumResult());
    }

    @Override
    public void add(AddParams params) throws BusinessException {
        ChannelRechargeDetail channelRechargeDetail = BeanUtils.copyProperties(params);
        channelRechargeDetail.setId(uidGenerator.generate());
        channelRechargeDetail.setCreateTime(new Date());
        this.repo.insert(channelRechargeDetail);
    }

    private QueryWrapper<ChannelRechargeDetail> buildQuery(QueryParams params) {
        QueryWrapper<ChannelRechargeDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(params.getChannelCode()), "channel_code", params.getChannelCode());
        queryWrapper.ge(params.getStartTime() != null, "time", params.getStartTime());
        queryWrapper.lt(params.getEndTime() != null, "time", params.getEndTime());
        queryWrapper.eq(params.getNetType() != null, "net_type", params.getNetType());
        return queryWrapper;
    }
}
