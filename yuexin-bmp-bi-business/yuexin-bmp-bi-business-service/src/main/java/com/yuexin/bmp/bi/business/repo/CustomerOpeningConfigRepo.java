package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.CustomerOpeningConfig;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-01-08 16:07:23
 */
@Repository
public interface CustomerOpeningConfigRepo extends BaseMapper<CustomerOpeningConfig> {
    long insertBatch(List<CustomerOpeningConfig> records);
}
