package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.CustomerProjectBudgetReportRepo;
import com.yuexin.bmp.bi.common.CustomerProjectBudgetReport;
import com.yuexin.bmp.bi.service.CustomerProjectBudgetReportService;
import com.yuexin.mybatis.PageUtils;
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
 * @since 2025-09-22 14:22:57
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class CustomerProjectBudgetReportServiceImpl implements CustomerProjectBudgetReportService {

    private final CustomerProjectBudgetReportRepo customerProjectBudgetReportRepo;

    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<CustomerProjectBudgetReport> groupQuery(QueryParams params) throws BusinessException {
        QueryWrapper<CustomerProjectBudgetReport> queryWrapper = buildQuery(params);
        queryWrapper.groupBy(params.getGroups());
        queryWrapper.select(String.join(",", params.getGroups()));
        if (params.getGroups().contains("dept_code")) {
            queryWrapper.orderByAsc("dept_code");
        }
        if (params.getGroups().contains("income_type")) {
            queryWrapper.orderByAsc("income_type");
        }
        return page(params.getPage(), queryWrapper);
    }

    @Override
    public PageResponse<CustomerProjectBudgetReport> query(QueryParams params) throws BusinessException {
        return page(params.getPage(), buildQuery(params));
    }

    private PageResponse<CustomerProjectBudgetReport> page(PageParams pageParams, QueryWrapper<CustomerProjectBudgetReport> queryWrapper) {
        if (pageParams != null) {
            Page<CustomerProjectBudgetReport> page = customerProjectBudgetReportRepo.selectPage(PageUtils.convert(pageParams), queryWrapper);
            return PageUtils.convert(page);
        } else {
            List<CustomerProjectBudgetReport> list = customerProjectBudgetReportRepo.selectList(queryWrapper);
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public List<CustomerProjectBudgetReport> sum(QueryParams params) throws BusinessException {
        QueryWrapper<CustomerProjectBudgetReport> queryWrapper = buildQuery(params);
        queryWrapper.select("budget_category_id",
                "SUM(budget_amount_begin) AS budget_amount_begin",
                "SUM(budget_amount_end) AS budget_amount_end",
                "SUM(budget_amount_diff) AS budget_amount_diff");
        queryWrapper.groupBy("budget_category_id");
        return customerProjectBudgetReportRepo.selectList(queryWrapper);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<CustomerProjectBudgetReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            CustomerProjectBudgetReport customerProjectBudgetReport = BeanUtils.copyProperties(params.get(i));
            customerProjectBudgetReport.setId(ids.get(i)).setCreateTime(new Date());
            list.add(customerProjectBudgetReport);
        });
        customerProjectBudgetReportRepo.insertBatch(list);
    }

    @Override
    public void delete(DelParams params) throws BusinessException {
        customerProjectBudgetReportRepo.delete(new LambdaQueryWrapper<CustomerProjectBudgetReport>()
                .eq(CustomerProjectBudgetReport::getYear, params.getYear())
                .eq(StringUtils.isNotBlank(params.getDeptCode()), CustomerProjectBudgetReport::getDeptCode, params.getDeptCode()));
    }

    private QueryWrapper<CustomerProjectBudgetReport> buildQuery(QueryParams params) {
        QueryWrapper<CustomerProjectBudgetReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(params.getDeptCode()), "dept_code", params.getDeptCode());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getDeptCodes()), "dept_code", params.getDeptCodes());
        queryWrapper.eq(params.getIncomeType() != null, "income_type", params.getIncomeType());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getIncomeTypes()), "income_type", params.getIncomeTypes());
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getProjectId() != null, "project_id", params.getProjectId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectIds()), "project_id", params.getProjectIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectLabelIds()), "project_label_id", params.getProjectLabelIds());
        queryWrapper.eq(params.getYear() != null, "year", params.getYear());
        queryWrapper.eq(params.getMonth() != null, "month", params.getMonth());
        queryWrapper.ge(params.getMonthStart() != null, "month", params.getMonthStart());
        queryWrapper.lt(params.getMonthEnd() != null, "month", params.getMonthEnd());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getAuthIds()), "leader_id", params.getAuthIds());
        return queryWrapper;
    }
}
