package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.CustomerBalanceReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author lxf
 * @since 2025-01-08 16:07:37
 */
@Repository
public interface CustomerBalanceReportRepo extends BaseMapper<CustomerBalanceReport> {
    long insertBatch(List<CustomerBalanceReport> records);
}
