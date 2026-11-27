package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.SupplierTradingReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 供应商交易报表(SupplierTradingReport)表数据库访问层
 *
 * @author lxf
 * @since 2024-01-23 10:26:34
 */
@Repository
public interface SupplierTradingReportRepo extends BaseMapper<SupplierTradingReport> {
    long insertBatch(List<SupplierTradingReport> reports);
}
