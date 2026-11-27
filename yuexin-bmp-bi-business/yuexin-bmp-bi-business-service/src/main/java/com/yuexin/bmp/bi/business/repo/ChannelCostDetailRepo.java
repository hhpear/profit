package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.ChannelCostDetail;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 平台利润明细(PlatformProfitDetail)表数据库访问层
 *
 * @author lxf
 * @since 2024-02-03 18:51:15
 */
@Repository
public interface ChannelCostDetailRepo extends BaseMapper<ChannelCostDetail> {
    long insertBatch(List<ChannelCostDetail> details);
}
