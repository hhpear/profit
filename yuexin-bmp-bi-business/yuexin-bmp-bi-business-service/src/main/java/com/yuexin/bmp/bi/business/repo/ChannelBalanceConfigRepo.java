package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.ChannelBalanceConfig;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-03-26 14:12:55
 */
@Repository
public interface ChannelBalanceConfigRepo extends BaseMapper<ChannelBalanceConfig> {
    long insertBatch(List<ChannelBalanceConfig> records);
}
