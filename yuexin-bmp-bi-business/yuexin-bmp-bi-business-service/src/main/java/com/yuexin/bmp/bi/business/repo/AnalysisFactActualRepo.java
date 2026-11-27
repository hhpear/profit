package com.yuexin.bmp.bi.business.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuexin.bmp.bi.common.AnalysisFactActual;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisFactActualRepo extends BaseMapper<AnalysisFactActual> {
    long insertBatch(List<AnalysisFactActual> list);
}
