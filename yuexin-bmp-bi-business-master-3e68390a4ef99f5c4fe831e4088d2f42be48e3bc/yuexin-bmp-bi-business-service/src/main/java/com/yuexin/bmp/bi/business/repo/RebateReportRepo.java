package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.RebateReport;
import org.springframework.stereotype.Repository;

import java.util.List;
/**
 * 返佣跟踪报表(RebateReport)表数据库访问层
 *
 * @author lxf
 * @since 2024-09-04 14:55:38
 */
@Repository
public interface RebateReportRepo extends BaseMapper<RebateReport> {
    long insertBatch(List<RebateReport> rebateReports);
}
