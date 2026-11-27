package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.SecurityDepositBalance;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author makejava
 * @since 2025-07-25 16:47:07
 */
@Repository
public interface SecurityDepositBalanceRepo extends BaseMapper<SecurityDepositBalance> {
    long insertBatch(List<SecurityDepositBalance> records);
}
