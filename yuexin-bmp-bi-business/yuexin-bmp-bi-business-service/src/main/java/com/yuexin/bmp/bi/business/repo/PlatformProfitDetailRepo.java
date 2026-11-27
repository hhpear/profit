package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.PlatformProfitDetail;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 平台利润明细(PlatformProfitDetail)表数据库访问层
 *
 * @author lxf
 * @since 2024-01-31 18:48:28
 */
@Repository
public interface PlatformProfitDetailRepo extends BaseMapper<PlatformProfitDetail> {
    long insertBatch(List<PlatformProfitDetail> details);
}
