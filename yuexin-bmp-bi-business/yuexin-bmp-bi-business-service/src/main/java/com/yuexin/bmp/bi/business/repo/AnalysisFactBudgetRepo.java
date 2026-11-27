package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.AnalysisFactBudget;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisFactBudgetRepo extends BaseMapper<AnalysisFactBudget> {
    long insertBatch(List<AnalysisFactBudget> list);
}
