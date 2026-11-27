package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.ChannelSyncDetailRepo;
import com.yuexin.bmp.bi.common.ChannelSyncDetail;
import com.yuexin.bmp.bi.service.ChannelSyncDetailService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lxf
 * @since 2024-11-20 13:45:23
 */
@Slf4j
@DubboService
public class ChannelSyncDetailServiceImpl implements ChannelSyncDetailService, NullHandler {
    @Resource
    private ChannelSyncDetailRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<ChannelSyncDetail> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<ChannelSyncDetail> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<ChannelSyncDetail> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public List<ChannelSyncDetail> queryGroup(QueryParams params) throws BusinessException {
        List<ChannelSyncDetail> list = this.repo.selectList(buildQuery(params));
        return list.stream().collect(Collectors.groupingBy(r -> DateUtils.truncate(r.getTime(), Calendar.DAY_OF_MONTH)))
                .entrySet().stream().map(entry -> {
                    ChannelSyncDetail firstValue = entry.getValue().stream()
                            .max(Comparator.comparing(ChannelSyncDetail::getTime))
                            .orElse(new ChannelSyncDetail());
                    return new ChannelSyncDetail().setTime(entry.getKey())
                            .setSyncUrls(firstValue.getSyncUrls())
                            .setOppBalance(firstValue.getOppBalance())
                            .setOurBalance(firstValue.getOurBalance())
                            .setDifference(firstValue.getDifference())
                            .setRemark(firstValue.getRemark());
                }).collect(Collectors.toList());
    }

    @Override
    public void add(AddParams params) throws BusinessException {
        ChannelSyncDetail channelSyncDetail = BeanUtils.copyProperties(params);
        channelSyncDetail.setId(uidGenerator.generate());
        channelSyncDetail.setCreateTime(new Date());
        channelSyncDetail.setDifference(channelSyncDetail.getOppBalance().subtract(channelSyncDetail.getOurBalance()));
        this.repo.insert(channelSyncDetail);
    }

    private QueryWrapper<ChannelSyncDetail> buildQuery(QueryParams params) {
        QueryWrapper<ChannelSyncDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(params.getChannelCode()), "channel_code", params.getChannelCode());
        queryWrapper.ge(params.getStartTime() != null, "time", params.getStartTime());
        queryWrapper.lt(params.getEndTime() != null, "time", params.getEndTime());
        queryWrapper.eq(params.getNetType() != null, "net_type", params.getNetType());
        return queryWrapper;
    }
}
