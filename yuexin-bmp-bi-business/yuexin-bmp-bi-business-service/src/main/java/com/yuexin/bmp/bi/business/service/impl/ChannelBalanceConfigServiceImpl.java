package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.ChannelBalanceConfigRepo;
import com.yuexin.bmp.bi.common.ChannelBalanceConfig;
import com.yuexin.bmp.bi.service.ChannelBalanceConfigService;
import com.yuexin.sequence.service.UidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lxf
 * @since 2025-03-26 14:12:57
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class ChannelBalanceConfigServiceImpl implements ChannelBalanceConfigService {

    private final ChannelBalanceConfigRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public List<ChannelBalanceConfig> multiGet(QueryParams params) throws BusinessException {
        return this.repo.selectList(buildQuery(params)).stream().map(this::convert).collect(Collectors.toList());
    }

    @Override
    public ChannelBalanceConfig getGlobal(GetParams params) throws BusinessException {
        ChannelBalanceConfig config = this.repo.selectOne(new LambdaQueryWrapper<ChannelBalanceConfig>()
                .eq(ChannelBalanceConfig::getType, ChannelBalanceConfig.Type.Global)
                .eq(ChannelBalanceConfig::getNetType, params.getNetType()));
        return convert(config);
    }

    @Override
    public ChannelBalanceConfig getExact(GetParams params) throws BusinessException {
        ChannelBalanceConfig config = this.repo.selectOne(new LambdaQueryWrapper<ChannelBalanceConfig>()
                .eq(ChannelBalanceConfig::getChannelId, params.getChannelId())
                .eq(ChannelBalanceConfig::getType, ChannelBalanceConfig.Type.Exact)
                .eq(ChannelBalanceConfig::getNetType, params.getNetType()));
        return convert(config);
    }

    private ChannelBalanceConfig convert(ChannelBalanceConfig config) {
        if (config != null) {
            config = JSONObject.parseObject(JSONObject.toJSONString(config), ChannelBalanceConfig.class);
        }
        return config;
    }

    @Override
    public void add(AddParams params) throws BusinessException {
        ChannelBalanceConfig config = BeanUtils.copyProperties(params)
                .setId(uidGenerator.generate()).setOperationTime(new Date());
        if (params.getChannelId() == null) {
            config.setType(ChannelBalanceConfig.Type.Global);
        } else {
            config.setType(ChannelBalanceConfig.Type.Exact);
        }
        this.repo.insert(config);
    }

    @Override
    public void modify(ModifyParams params) throws BusinessException {
        ChannelBalanceConfig config = BeanUtils.copyProperties(params)
                .setOperationTime(new Date());
        this.repo.updateById(config);
    }

    @Override
    public void delete(DeleteParams params) throws BusinessException {
        this.repo.delete(new LambdaQueryWrapper<ChannelBalanceConfig>()
                .eq(ChannelBalanceConfig::getChannelId, params.getChannelId())
                .eq(ChannelBalanceConfig::getType, ChannelBalanceConfig.Type.Exact)
                .eq(ChannelBalanceConfig::getNetType, params.getNetType()));
    }

    private LambdaQueryWrapper<ChannelBalanceConfig> buildQuery(QueryParams params) {
        LambdaQueryWrapper<ChannelBalanceConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getChannelIds()), ChannelBalanceConfig::getChannelId, params.getChannelIds());
        return queryWrapper;
    }
}
