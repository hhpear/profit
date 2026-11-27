package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.DeptBudgetReportRepo;
import com.yuexin.bmp.bi.common.DeptBudgetReport;
import com.yuexin.bmp.bi.service.DeptBudgetReportService;
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

/**
 * @author lxf
 * @since 2025-09-22 14:23:46
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class DeptBudgetReportServiceImpl implements DeptBudgetReportService {

    private final DeptBudgetReportRepo deptBudgetReportRepo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public List<DeptBudgetReport> query(QueryParams params) throws BusinessException {
        return deptBudgetReportRepo.selectList(buildQuery(params));
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<DeptBudgetReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            DeptBudgetReport deptBudgetReport = BeanUtils.copyProperties(params.get(i));
            deptBudgetReport.setId(ids.get(i)).setCreateTime(new Date());
            list.add(deptBudgetReport);
        });
        deptBudgetReportRepo.insertBatch(list);
    }

    @Override
    public void delete(DelParams params) throws BusinessException {
        deptBudgetReportRepo.delete(new LambdaQueryWrapper<DeptBudgetReport>()
                .eq(DeptBudgetReport::getYear, params.getYear())
                .eq(StringUtils.isNotBlank(params.getDeptCode()), DeptBudgetReport::getDeptCode, params.getDeptCode()));
    }

    private LambdaQueryWrapper<DeptBudgetReport> buildQuery(QueryParams params) {
        LambdaQueryWrapper<DeptBudgetReport> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(params.getDeptCode()), DeptBudgetReport::getDeptCode, params.getDeptCode());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getDeptCodes()), DeptBudgetReport::getDeptCode, params.getDeptCodes());
        queryWrapper.eq(params.getBudgetCategoryId() != null, DeptBudgetReport::getBudgetCategoryId, params.getBudgetCategoryId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), DeptBudgetReport::getBudgetCategoryId, params.getBudgetCategoryIds());
        queryWrapper.eq(params.getYear() != null, DeptBudgetReport::getYear, params.getYear());
        queryWrapper.eq(params.getMonth() != null, DeptBudgetReport::getMonth, params.getMonth());
        queryWrapper.ge(params.getMonthStart() != null, DeptBudgetReport::getMonth, params.getMonthStart());
        queryWrapper.lt(params.getMonthEnd() != null, DeptBudgetReport::getMonth, params.getMonthEnd());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getAuthDeptCodes()), DeptBudgetReport::getDeptCode, params.getAuthDeptCodes());
        return queryWrapper;
    }
}
