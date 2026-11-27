package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.CustomerTradingReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 客户交易报表(CustomerTradingReport)表数据库访问层
 *
 * @author lxf
 * @since 2024-01-23 10:26:06
 */
@Repository
public interface CustomerTradingReportRepo extends BaseMapper<CustomerTradingReport> {
    long insertBatch(List<CustomerTradingReport> reports);
}
