package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.PostpaidCustomerReport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 后付费客户跟踪报表(PostpaidCustomerReport)表数据库访问层
 *
 * @author lxf
 * @since 2024-06-05 14:21:12
 */
@Repository
public interface PostpaidCustomerReportRepo extends BaseMapper<PostpaidCustomerReport> {
    long insertBatch(List<PostpaidCustomerReport> reports);
}
