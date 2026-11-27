package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.SupplierOpeningConfig;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-01-08 16:06:23
 */
@Repository
public interface SupplierOpeningConfigRepo extends BaseMapper<SupplierOpeningConfig> {
    long insertBatch(List<SupplierOpeningConfig> records);
}
