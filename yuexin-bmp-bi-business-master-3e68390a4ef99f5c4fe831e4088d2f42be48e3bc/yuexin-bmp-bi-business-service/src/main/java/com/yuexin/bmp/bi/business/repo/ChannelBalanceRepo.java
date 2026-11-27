package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.ChannelBalance;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2024-11-20 13:46:08
 */
@Repository
public interface ChannelBalanceRepo extends BaseMapper<ChannelBalance> {
    long insertBatch(List<ChannelBalance> channelBalances);
}
