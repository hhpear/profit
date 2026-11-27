package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.CustomerProfitReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 客户利润报表(CustomerProfitReport)表数据库访问层
 *
 * @author lxf
 * @since 2024-01-23 10:25:28
 */
@Repository
public interface CustomerProfitReportRepo extends BaseMapper<CustomerProfitReport> {
    long insertBatch(List<CustomerProfitReport> reports);
}
