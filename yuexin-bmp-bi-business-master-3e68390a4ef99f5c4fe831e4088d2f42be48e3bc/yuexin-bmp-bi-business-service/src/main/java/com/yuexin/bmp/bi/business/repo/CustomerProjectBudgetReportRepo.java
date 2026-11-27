package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.CustomerProjectBudgetReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-09-22 14:22:56
 */
@Repository
public interface CustomerProjectBudgetReportRepo extends BaseMapper<CustomerProjectBudgetReport> {
    long insertBatch(List<CustomerProjectBudgetReport> records);
}
