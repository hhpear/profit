package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.DeptBudgetReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-09-22 14:23:46
 */
@Repository
public interface DeptBudgetReportRepo extends BaseMapper<DeptBudgetReport> {
    long insertBatch(List<DeptBudgetReport> records);
}
