package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.ChannelBalanceLogRepo;
import com.yuexin.bmp.bi.common.ChannelBalanceLog;
import com.yuexin.bmp.bi.service.ChannelBalanceLogService;
import com.yuexin.sequence.service.UidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @author lxf
 * @since 2025-03-26 14:13:12
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class ChannelBalanceLogServiceImpl implements ChannelBalanceLogService {

    private final ChannelBalanceLogRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public ChannelBalanceLog get(GetParams params) throws BusinessException {
        LambdaQueryWrapper<ChannelBalanceLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChannelBalanceLog::getChannelId, params.getChannelId());
        queryWrapper.eq(ChannelBalanceLog::getNetType, params.getNetType());
        queryWrapper.eq(ChannelBalanceLog::getState, params.getState());
        queryWrapper.last("order by id desc limit 1");
        return this.repo.selectOne(queryWrapper);
    }

    @Override
    public List<ChannelBalanceLog> multiGet(MultiGetParams params) throws BusinessException {
        LambdaQueryWrapper<ChannelBalanceLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ChannelBalanceLog::getChannelId, params.getChannelIds());
        queryWrapper.ge(ChannelBalanceLog::getAlarmTime, params.getStartTime());
        return this.repo.selectList(queryWrapper);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<ChannelBalanceLog> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            ChannelBalanceLog balanceLog = BeanUtils.copyProperties(params.get(i));
            balanceLog.setId(ids.get(i));
            balanceLog.setAlarmTime(new Date());
            list.add(balanceLog);
        });
        this.repo.insertBatch(list);
    }
}
