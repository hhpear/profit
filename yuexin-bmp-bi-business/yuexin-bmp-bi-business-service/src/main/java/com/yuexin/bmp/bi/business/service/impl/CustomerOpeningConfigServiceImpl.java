package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.CustomerOpeningConfigRepo;
import com.yuexin.bmp.bi.common.CustomerOpeningConfig;
import com.yuexin.bmp.bi.service.CustomerOpeningConfigService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @author lxf
 * @since 2025-01-08 16:07:23
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class CustomerOpeningConfigServiceImpl implements CustomerOpeningConfigService {

    private final CustomerOpeningConfigRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<CustomerOpeningConfig> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<CustomerOpeningConfig> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<CustomerOpeningConfig> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<CustomerOpeningConfig> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            CustomerOpeningConfig record = BeanUtils.copyProperties(params.get(i));
            record.setId(ids.get(i));
            record.setOperateTime(new Date());
            list.add(record);
        });
        this.repo.insertBatch(list);
    }

    private LambdaQueryWrapper<CustomerOpeningConfig> buildQuery(QueryParams params) {
        LambdaQueryWrapper<CustomerOpeningConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, CustomerOpeningConfig::getCustomerId, params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), CustomerOpeningConfig::getCustomerId, params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, CustomerOpeningConfig::getBusinessBodyId, params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), CustomerOpeningConfig::getBusinessBodyId, params.getBusinessBodyIds());
        return queryWrapper;
    }
}
