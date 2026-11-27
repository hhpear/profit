package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.SupplierBalanceReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-01-08 16:06:43
 */
@Repository
public interface SupplierBalanceReportRepo extends BaseMapper<SupplierBalanceReport> {
    long insertBatch(List<SupplierBalanceReport> records);
}
