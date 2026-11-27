package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.PlatformProfitReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 平台利润报表(PlatformProfitReport)表数据库访问层
 *
 * @author lxf
 * @since 2024-01-23 10:26:59
 */
@Repository
public interface PlatformProfitReportRepo extends BaseMapper<PlatformProfitReport> {
    long insertBatch(List<PlatformProfitReport> reports);
}
