package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.AnalysisFactActualRepo;
import com.yuexin.bmp.bi.common.AnalysisFactActual;
import com.yuexin.bmp.bi.service.AnalysisFactActualService;
import com.yuexin.sequence.service.UidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class AnalysisFactActualServiceImpl implements AnalysisFactActualService {

    private final AnalysisFactActualRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public List<AnalysisFactActual> query(QueryParams params) throws BusinessException {
        return repo.selectList(buildQuery(params));
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<AnalysisFactActual> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            AnalysisFactActual actual = BEAN_CONVERTER.copyProperties(params.get(i));
            actual.setId(ids.get(i))
                    .setDeleted(0)
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date());
            list.add(actual);
        });
        repo.insertBatch(list);
    }

    @Override
    public void delete(DeleteParams params) throws BusinessException {
        repo.delete(new LambdaQueryWrapper<AnalysisFactActual>()
                .eq(params.getSourceType() != null, AnalysisFactActual::getSourceType, params.getSourceType())
                .ge(params.getStatMonthStart() != null, AnalysisFactActual::getStatMonth, params.getStatMonthStart())
                .lt(params.getStatMonthEnd() != null, AnalysisFactActual::getStatMonth, params.getStatMonthEnd()));
    }

    private LambdaQueryWrapper<AnalysisFactActual> buildQuery(QueryParams params) {
        LambdaQueryWrapper<AnalysisFactActual> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(params.getSourceType() != null, AnalysisFactActual::getSourceType, params.getSourceType());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getSourceTypes()), AnalysisFactActual::getSourceType, params.getSourceTypes());
        queryWrapper.eq(params.getStatMonth() != null, AnalysisFactActual::getStatMonth, params.getStatMonth());
        queryWrapper.ge(params.getStatMonthStart() != null, AnalysisFactActual::getStatMonth, params.getStatMonthStart());
        queryWrapper.lt(params.getStatMonthEnd() != null, AnalysisFactActual::getStatMonth, params.getStatMonthEnd());
        queryWrapper.eq(params.getProjectId() != null, AnalysisFactActual::getProjectId, params.getProjectId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectIds()), AnalysisFactActual::getProjectId, params.getProjectIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getDeptCode()), AnalysisFactActual::getDeptCode, params.getDeptCode());
        queryWrapper.eq(params.getLeaderId() != null, AnalysisFactActual::getLeaderId, params.getLeaderId());
        queryWrapper.eq(AnalysisFactActual::getDeleted, 0);
        return queryWrapper;
    }
}
