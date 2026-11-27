package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.AnalysisFactBudgetRepo;
import com.yuexin.bmp.bi.common.AnalysisFactBudget;
import com.yuexin.bmp.bi.service.AnalysisFactBudgetService;
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
public class AnalysisFactBudgetServiceImpl implements AnalysisFactBudgetService {

    private final AnalysisFactBudgetRepo repo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public List<AnalysisFactBudget> query(QueryParams params) throws BusinessException {
        return repo.selectList(buildQuery(params));
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        if (CollectionUtils.isEmpty(params)) {
            return;
        }
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<AnalysisFactBudget> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            AnalysisFactBudget budget = BEAN_CONVERTER.copyProperties(params.get(i));
            budget.setId(ids.get(i))
                    .setDeleted(0)
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date());
            list.add(budget);
        });
        repo.insertBatch(list);
    }

    @Override
    public void delete(DeleteParams params) throws BusinessException {
        repo.delete(new LambdaQueryWrapper<AnalysisFactBudget>()
                .eq(params.getBizType() != null, AnalysisFactBudget::getBizType, params.getBizType())
                .ge(params.getStatMonthStart() != null, AnalysisFactBudget::getStatMonth, params.getStatMonthStart())
                .lt(params.getStatMonthEnd() != null, AnalysisFactBudget::getStatMonth, params.getStatMonthEnd()));
    }

    private LambdaQueryWrapper<AnalysisFactBudget> buildQuery(QueryParams params) {
        LambdaQueryWrapper<AnalysisFactBudget> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(params.getBizType() != null, AnalysisFactBudget::getBizType, params.getBizType());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBizTypes()), AnalysisFactBudget::getBizType, params.getBizTypes());
        queryWrapper.eq(params.getStatMonth() != null, AnalysisFactBudget::getStatMonth, params.getStatMonth());
        queryWrapper.ge(params.getStatMonthStart() != null, AnalysisFactBudget::getStatMonth, params.getStatMonthStart());
        queryWrapper.lt(params.getStatMonthEnd() != null, AnalysisFactBudget::getStatMonth, params.getStatMonthEnd());
        queryWrapper.eq(params.getProjectId() != null, AnalysisFactBudget::getProjectId, params.getProjectId());
        queryWrapper.eq(StringUtils.isNotBlank(params.getDeptCode()), AnalysisFactBudget::getDeptCode, params.getDeptCode());
        queryWrapper.eq(params.getLeaderId() != null, AnalysisFactBudget::getLeaderId, params.getLeaderId());
        queryWrapper.eq(AnalysisFactBudget::getDeleted, 0);
        return queryWrapper;
    }
}
