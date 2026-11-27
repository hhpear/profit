package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.ChannelBalanceLog;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-03-26 14:13:12
 */
@Repository
public interface ChannelBalanceLogRepo extends BaseMapper<ChannelBalanceLog> {
    long insertBatch(List<ChannelBalanceLog> records);
}
