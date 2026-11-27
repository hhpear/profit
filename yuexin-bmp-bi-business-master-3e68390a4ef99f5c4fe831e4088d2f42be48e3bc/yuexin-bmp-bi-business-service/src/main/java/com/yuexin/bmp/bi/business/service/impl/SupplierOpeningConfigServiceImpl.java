package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.SupplierOpeningConfigRepo;
import com.yuexin.bmp.bi.common.SupplierOpeningConfig;
import com.yuexin.bmp.bi.service.SupplierOpeningConfigService;
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
 * @since 2025-01-08 16:06:25
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class SupplierOpeningConfigServiceImpl implements SupplierOpeningConfigService {

    private final SupplierOpeningConfigRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<SupplierOpeningConfig> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<SupplierOpeningConfig> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<SupplierOpeningConfig> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<SupplierOpeningConfig> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            SupplierOpeningConfig record = BeanUtils.copyProperties(params.get(i));
            record.setId(ids.get(i));
            record.setOperateTime(new Date());
            list.add(record);
        });
        this.repo.insertBatch(list);
    }

    private LambdaQueryWrapper<SupplierOpeningConfig> buildQuery(QueryParams params) {
        LambdaQueryWrapper<SupplierOpeningConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(params.getSupplierId() != null, SupplierOpeningConfig::getSupplierId, params.getSupplierId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getSupplierIds()), SupplierOpeningConfig::getSupplierId, params.getSupplierIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, SupplierOpeningConfig::getBusinessBodyId, params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), SupplierOpeningConfig::getBusinessBodyId, params.getBusinessBodyIds());
        return queryWrapper;
    }
}
