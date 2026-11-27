package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.AnalysisFactActualRepo;
import com.yuexin.bmp.bi.business.repo.AnalysisFactBudgetRepo;
import com.yuexin.bmp.bi.common.*;
import com.yuexin.bmp.bi.service.DeptBudgetActualAnalysisService;
import com.yuexin.bmp.finance.business.BudgetCategoryService;
import com.yuexin.bmp.finance.model.OaBudgetCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class DeptBudgetActualAnalysisServiceImpl implements DeptBudgetActualAnalysisService {

    private static final List<RowDefinition> ROW_DEFINITIONS = Arrays.asList(
            RowDefinition.parent("income", "收入", OaCategoryGroup.INCOME),
            RowDefinition.child("income_child", OaCategoryGroup.INCOME),
            RowDefinition.parent("cost", "成本", OaCategoryGroup.COST),
            RowDefinition.child("cost_child", OaCategoryGroup.COST),
            RowDefinition.calc("gross_profit", "毛利"),
            RowDefinition.calc("gross_profit_rate", "毛利率"),
            RowDefinition.parent("daily_fee", "日常费用", OaCategoryGroup.FEE),
            RowDefinition.child("daily_fee_child", OaCategoryGroup.FEE),
            RowDefinition.calc("net_profit", "净利"),
            RowDefinition.calc("net_profit_rate", "净利率")
    );

    private final AnalysisFactBudgetRepo analysisFactBudgetRepo;
    private final AnalysisFactActualRepo analysisFactActualRepo;
    @DubboReference
    private BudgetCategoryService budgetCategoryService;

    @Override
    public List<QueryVO> query(QueryParams params) throws BusinessException {
        QueryParams safeParams = normalize(params);
        List<AnalysisFactBudget> budgetFacts = analysisFactBudgetRepo.selectList(buildBudgetQuery(safeParams));
        List<AnalysisFactActual> actualFacts = analysisFactActualRepo.selectList(buildActualQuery(safeParams));
        List<String> deptCodes = resolveDeptCodes(safeParams, budgetFacts, actualFacts);
        if (CollectionUtils.isEmpty(deptCodes) && !"TOTAL".equalsIgnoreCase(safeParams.getDisplayMode())) {
            return Collections.emptyList();
        }
        List<String> timeKeys = buildTimeKeys(safeParams.getTimeGranularity(), safeParams.getRangeStart(), safeParams.getRangeEnd());
        Map<Long, OaBudgetCategory> categoryMap = loadBudgetCategoryMap(budgetFacts, actualFacts);
        Map<String, List<AnalysisFactBudget>> budgetByDept = budgetFacts.stream().collect(Collectors.groupingBy(f -> blankToTotal(f.getDeptCode())));
        Map<String, List<AnalysisFactActual>> actualByDept = actualFacts.stream().collect(Collectors.groupingBy(f -> blankToTotal(f.getDeptCode())));
        List<QueryVO> result = new ArrayList<>();
        if ("TOTAL".equalsIgnoreCase(safeParams.getDisplayMode())) {
            result.addAll(buildDeptRows(null, timeKeys, budgetFacts, actualFacts, safeParams.getAmountType(), safeParams.getTimeGranularity(), categoryMap));
            return result;
        }
        for (String deptCode : deptCodes) {
            result.addAll(buildDeptRows(deptCode, timeKeys,
                    budgetByDept.getOrDefault(blankToTotal(deptCode), Collections.emptyList()),
                    actualByDept.getOrDefault(blankToTotal(deptCode), Collections.emptyList()),
                    safeParams.getAmountType(), safeParams.getTimeGranularity(), categoryMap));
        }
        return result;
    }

    @Override
    public BudgetDrilldownVO drilldownBudget(BudgetDrilldownParams params) throws BusinessException {
        Date monthStart = resolveBucketStart(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        Date monthEndExclusive = resolveBucketEndExclusive(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        return new BudgetDrilldownVO()
                .setDeptCodes(StringUtils.isBlank(params.getDeptCode()) ? Collections.emptyList() : Collections.singletonList(params.getDeptCode()))
                .setBudgetCategoryIds(defaultBudgetCategoryIds(params.getBudgetCategoryIds()))
                .setMonthStart(monthStart)
                .setMonthEnd(DateUtils.addDays(monthEndExclusive, -1))
                .setApprovalStates(Collections.singletonList("Agreed"))
                .setInputTypes("beginBudget".equals(params.getMetricField()) ? Collections.singletonList("Input") : Arrays.asList("Input", "Adjust"));
    }

    @Override
    public ActualDrilldownVO drilldownActual(ActualDrilldownParams params) throws BusinessException {
        Date monthStart = resolveBucketStart(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        Date monthEndExclusive = resolveBucketEndExclusive(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        List<AnalysisFactActual> facts = analysisFactActualRepo.selectList(new LambdaQueryWrapper<AnalysisFactActual>()
                .eq(AnalysisFactActual::getDeleted, 0)
                .ge(AnalysisFactActual::getStatMonth, monthStart)
                .lt(AnalysisFactActual::getStatMonth, monthEndExclusive)
                .eq(StringUtils.isNotBlank(params.getDeptCode()), AnalysisFactActual::getDeptCode, params.getDeptCode())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactActual::getBudgetCategoryId, params.getBudgetCategoryIds())
                .in(CollectionUtils.isNotEmpty(params.getAuthLeaderIds()), AnalysisFactActual::getLeaderId, params.getAuthLeaderIds()));
        List<DrilldownTabVO> tabs = new ArrayList<>();
        for (AnalysisSourceType sourceType : AnalysisSourceType.values()) {
            List<AnalysisFactActual> sourceFacts = facts.stream().filter(f -> sourceType == f.getSourceType()).collect(Collectors.toList());
            tabs.add(buildActualTab(sourceType, sourceFacts, params.getAmountType()));
        }
        return new ActualDrilldownVO().setTabs(tabs);
    }

    private List<QueryVO> buildDeptRows(String deptCode, List<String> timeKeys, List<AnalysisFactBudget> budgetFacts,
                                        List<AnalysisFactActual> actualFacts, AmountType amountType, TimeGranularity granularity,
                                        Map<Long, OaBudgetCategory> categoryMap) {
        List<QueryVO> rows = new ArrayList<>();
        for (RowDefinition definition : ROW_DEFINITIONS) {
            if (definition.kind == RowKind.CHILD) {
                List<Long> categoryIds = collectCategoryIds(definition.group, budgetFacts, actualFacts, categoryMap);
                for (Long categoryId : categoryIds) {
                    rows.add(buildRow(deptCode, definition.group.name().toLowerCase() + ":" + categoryId, AnalysisRowType.CHILD, null,
                            categoryId, true, timeKeys,
                            budgetFacts.stream().filter(f -> Objects.equals(categoryId, f.getBudgetCategoryId())).collect(Collectors.toList()),
                            actualFacts.stream().filter(f -> Objects.equals(categoryId, f.getBudgetCategoryId())).collect(Collectors.toList()),
                            amountType, granularity));
                }
                continue;
            }
            if (definition.kind == RowKind.PARENT) {
                Long parentId = findParentId(definition.group, budgetFacts, actualFacts, categoryMap);
                rows.add(buildRow(deptCode, definition.key, AnalysisRowType.PARENT, definition.name, parentId, true, timeKeys,
                        budgetFacts.stream().filter(f -> isInGroup(definition.group, f.getBudgetParentId(), f.getBudgetCategoryId(), categoryMap)).collect(Collectors.toList()),
                        actualFacts.stream().filter(f -> isInGroup(definition.group, f.getBudgetParentId(), f.getBudgetCategoryId(), categoryMap)).collect(Collectors.toList()),
                        amountType, granularity));
                continue;
            }
            rows.add(buildCalcRow(deptCode, definition.key, definition.name, timeKeys, rows));
        }
        return rows;
    }

    private QueryVO buildRow(String deptCode, String rowKey, AnalysisRowType rowType, String rowName, Long categoryId, Boolean expanded,
                             List<String> timeKeys, List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts,
                             AmountType amountType, TimeGranularity granularity) {
        QueryVO row = new QueryVO()
                .setDeptCode(deptCode)
                .setRowKey(rowKey)
                .setRowType(rowType)
                .setRowName(rowName == null ? String.valueOf(categoryId) : rowName)
                .setExpanded(expanded)
                .setCells(new LinkedHashMap<>());
        if (rowType == AnalysisRowType.PARENT) {
            row.setParentBudgetCategoryId(categoryId);
        } else if (rowType == AnalysisRowType.CHILD) {
            row.setBudgetCategoryId(categoryId);
        }
        for (String timeKey : timeKeys) {
            BigDecimal beginBudget = sumBudget(budgetFacts, timeKey, true, granularity);
            BigDecimal endBudget = sumBudget(budgetFacts, timeKey, false, granularity);
            BigDecimal actualAmount = sumActual(actualFacts, timeKey, amountType, granularity);
            row.getCells().put(timeKey, new MetricCellVO()
                    .setBeginBudget(beginBudget)
                    .setEndBudget(endBudget)
                    .setActualAmount(actualAmount)
                    .setDiffAmount(endBudget.subtract(actualAmount))
                    .setReachRate(calcRate(actualAmount, endBudget)));
        }
        return row;
    }

    private QueryVO buildCalcRow(String deptCode, String rowKey, String rowName, List<String> timeKeys, List<QueryVO> builtRows) {
        QueryVO row = new QueryVO()
                .setDeptCode(deptCode)
                .setRowKey(rowKey)
                .setRowType(AnalysisRowType.CALC)
                .setRowName(rowName)
                .setExpanded(false)
                .setCells(new LinkedHashMap<>());
        QueryVO incomeRow = findRow(builtRows, "income");
        QueryVO costRow = findRow(builtRows, "cost");
        QueryVO feeRow = findRow(builtRows, "daily_fee");
        for (String timeKey : timeKeys) {
            MetricCellVO incomeCell = incomeRow == null ? new MetricCellVO() : incomeRow.getCells().get(timeKey);
            MetricCellVO costCell = costRow == null ? new MetricCellVO() : costRow.getCells().get(timeKey);
            MetricCellVO feeCell = feeRow == null ? new MetricCellVO() : feeRow.getCells().get(timeKey);
            BigDecimal incomeEnd = safeValue(incomeCell == null ? null : incomeCell.getEndBudget());
            BigDecimal costEnd = safeValue(costCell == null ? null : costCell.getEndBudget());
            BigDecimal feeEnd = safeValue(feeCell == null ? null : feeCell.getEndBudget());
            BigDecimal incomeActual = safeValue(incomeCell == null ? null : incomeCell.getActualAmount());
            BigDecimal costActual = safeValue(costCell == null ? null : costCell.getActualAmount());
            BigDecimal feeActual = safeValue(feeCell == null ? null : feeCell.getActualAmount());
            if ("gross_profit".equals(rowKey)) {
                row.getCells().put(timeKey, new MetricCellVO()
                        .setBeginBudget(safeValue(incomeCell == null ? null : incomeCell.getBeginBudget()).subtract(safeValue(costCell == null ? null : costCell.getBeginBudget())))
                        .setEndBudget(incomeEnd.subtract(costEnd))
                        .setActualAmount(incomeActual.subtract(costActual))
                        .setDiffAmount(incomeEnd.subtract(costEnd).subtract(incomeActual.subtract(costActual)))
                        .setReachRate(calcRate(incomeActual.subtract(costActual), incomeEnd.subtract(costEnd))));
            } else if ("gross_profit_rate".equals(rowKey)) {
                BigDecimal endAmount = incomeEnd.subtract(costEnd);
                BigDecimal actualAmount = incomeActual.subtract(costActual);
                row.getCells().put(timeKey, new MetricCellVO()
                        .setDiffAmount(null)
                        .setBeginBudget(calcRate(safeValue(incomeCell == null ? null : incomeCell.getBeginBudget()).subtract(safeValue(costCell == null ? null : costCell.getBeginBudget())),
                                safeValue(incomeCell == null ? null : incomeCell.getBeginBudget())))
                        .setEndBudget(calcRate(endAmount, incomeEnd))
                        .setActualAmount(calcRate(actualAmount, incomeActual))
                        .setReachRate(null));
            } else if ("net_profit".equals(rowKey)) {
                BigDecimal endAmount = incomeEnd.subtract(costEnd).subtract(feeEnd);
                BigDecimal actualAmount = incomeActual.subtract(costActual).subtract(feeActual);
                row.getCells().put(timeKey, new MetricCellVO()
                        .setBeginBudget(safeValue(incomeCell == null ? null : incomeCell.getBeginBudget())
                                .subtract(safeValue(costCell == null ? null : costCell.getBeginBudget()))
                                .subtract(safeValue(feeCell == null ? null : feeCell.getBeginBudget())))
                        .setEndBudget(endAmount)
                        .setActualAmount(actualAmount)
                        .setDiffAmount(endAmount.subtract(actualAmount))
                        .setReachRate(calcRate(actualAmount, endAmount)));
            } else if ("net_profit_rate".equals(rowKey)) {
                BigDecimal endAmount = incomeEnd.subtract(costEnd).subtract(feeEnd);
                BigDecimal actualAmount = incomeActual.subtract(costActual).subtract(feeActual);
                row.getCells().put(timeKey, new MetricCellVO()
                        .setDiffAmount(null)
                        .setBeginBudget(calcRate(safeValue(incomeCell == null ? null : incomeCell.getBeginBudget())
                                        .subtract(safeValue(costCell == null ? null : costCell.getBeginBudget()))
                                        .subtract(safeValue(feeCell == null ? null : feeCell.getBeginBudget())),
                                safeValue(incomeCell == null ? null : incomeCell.getBeginBudget())))
                        .setEndBudget(calcRate(endAmount, incomeEnd))
                        .setActualAmount(calcRate(actualAmount, incomeActual))
                        .setReachRate(null));
            }
        }
        return row;
    }

    private QueryVO findRow(List<QueryVO> rows, String rowKey) {
        return rows.stream().filter(r -> rowKey.equals(r.getRowKey())).findFirst().orElse(null);
    }

    private List<Long> collectCategoryIds(OaCategoryGroup group, List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts,
                                          Map<Long, OaBudgetCategory> categoryMap) {
        return concatCategoryIds(budgetFacts, actualFacts).stream()
                .filter(Objects::nonNull)
                .filter(categoryId -> isInGroup(group, null, categoryId, categoryMap))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<Long> concatCategoryIds(List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts) {
        List<Long> ids = new ArrayList<>();
        ids.addAll(budgetFacts.stream().map(AnalysisFactBudget::getBudgetCategoryId).filter(Objects::nonNull).collect(Collectors.toList()));
        ids.addAll(actualFacts.stream().map(AnalysisFactActual::getBudgetCategoryId).filter(Objects::nonNull).collect(Collectors.toList()));
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private Long findParentId(OaCategoryGroup group, List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts,
                              Map<Long, OaBudgetCategory> categoryMap) {
        return concatParentIds(budgetFacts, actualFacts).stream()
                .filter(parentId -> isInGroup(group, parentId, null, categoryMap))
                .findFirst().orElse(null);
    }

    private List<Long> concatParentIds(List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts) {
        List<Long> ids = new ArrayList<>();
        ids.addAll(budgetFacts.stream().map(AnalysisFactBudget::getBudgetParentId).filter(Objects::nonNull).collect(Collectors.toList()));
        ids.addAll(actualFacts.stream().map(AnalysisFactActual::getBudgetParentId).filter(Objects::nonNull).collect(Collectors.toList()));
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private DrilldownTabVO buildActualTab(AnalysisSourceType sourceType, List<AnalysisFactActual> sourceFacts, AmountType amountType) {
        List<ColumnVO> columns = buildActualColumns(sourceType);
        List<Map<String, Object>> rows = sourceFacts.stream().map(f -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("statMonth", f.getStatMonth());
            row.put("sourceCode", f.getSourceCode());
            row.put("budgetCategoryId", f.getBudgetCategoryId());
            row.put("projectId", f.getProjectId());
            row.put("leaderId", f.getLeaderId());
            row.put("deptCode", f.getDeptCode());
            row.put("approvalState", f.getApprovalState());
            row.put("sourceSubType", f.getSourceSubType());
            row.put("bizDate", f.getBizDate());
            row.put("orderProperty", f.getOrderProperty());
            row.put("orderState", f.getOrderState());
            row.put("platform", f.getPlatform());
            row.put("entCode", f.getEntCode());
            row.put("entName", f.getEntName());
            row.put("accountNumber", f.getAccountNumber());
            row.put("feeCount", f.getFeeCount());
            row.put("actualOwnerId", f.getActualOwnerId());
            row.put("summary", f.getSummary());
            row.put("submitterId", f.getSubmitterId());
            row.put("applyTime", f.getApplyTime());
            row.put("approveTime", f.getApproveTime());
            row.put("accountDate", f.getAccountDate());
            row.put("linkOaId", f.getLinkOaId());
            row.put("linkOaType", f.getLinkOaType());
            row.put("supplierName", f.getSupplierName());
            row.put("approvalCode", f.getApprovalCode());
            row.put("channelCode", f.getChannelCode());
            row.put("channelName", f.getChannelName());
            row.put("accountName", f.getAccountName());
            row.put("firstAgentRule", f.getFirstAgentRule());
            row.put("secondAgentRule", f.getSecondAgentRule());
            row.put("thirdAgentRule", f.getThirdAgentRule());
            row.put("linkOaCode", f.getLinkOaCode());
            row.put("amountTax", safeValue(f.getAmountTax()));
            row.put("amountUntax", safeValue(f.getAmountUntax()));
            row.put("amount", AmountType.UNTAX == amountType ? safeValue(f.getAmountUntax()) : safeValue(f.getAmountTax()));
            return row;
        }).collect(Collectors.toList());
        return new DrilldownTabVO()
                .setSourceType(sourceType.name())
                .setSourceName(sourceType.getName())
                .setColumns(columns)
                .setRows(rows)
                .setSummaryCount((long) sourceFacts.size())
                .setSummaryAmount(sourceFacts.stream().map(f -> AmountType.UNTAX == amountType ? safeValue(f.getAmountUntax()) : safeValue(f.getAmountTax()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private List<ColumnVO> buildActualColumns(AnalysisSourceType sourceType) {
        List<ColumnVO> columns = new ArrayList<>();
        switch (sourceType) {
            case SMS_INCOME:
                addColumns(columns, "customerId", "归属客户", "platform", "平台", "entName", "大平台企业名称",
                        "projectId", "归属项目", "leaderId", "归属员工", "deptCode", "归属部门", "statMonth", "月份",
                        "amountTax", "短信收入(含税)", "amountUntax", "短信收入(未税)");
                break;
            case SERVICE_ORDER_INCOME:
                addColumns(columns, "sourceCode", "订单号", "customerId", "归属客户", "projectId", "归属项目",
                        "leaderId", "归属员工", "deptCode", "归属部门", "bizDate", "订单日期", "orderProperty", "属性",
                        "amountTax", "含税金额", "amountUntax", "未税金额", "orderState", "订单状态", "approvalCode", "审批编号");
                break;
            case CHANNEL_COST:
                addColumns(columns, "customerId", "归属客户", "platform", "平台", "entName", "大平台企业名称",
                        "supplierName", "供应商", "channelCode", "通道编码", "channelName", "通道名称", "projectId", "归属项目",
                        "leaderId", "归属员工", "deptCode", "归属部门", "statMonth", "月份", "amountTax", "通道成本(含税)",
                        "amountUntax", "通道成本(未税)");
                break;
            case REBATE:
                addColumns(columns, "customerId", "归属客户", "entName", "大平台企业名称", "accountNumber", "账号",
                        "accountName", "账号名称", "projectId", "归属项目", "leaderId", "归属员工", "deptCode", "归属部门",
                        "statMonth", "月份", "feeCount", "计费数", "firstAgentRule", "一级代理费规则",
                        "secondAgentRule", "二级代理费规则", "thirdAgentRule", "三级代理费规则",
                        "amountTax", "返佣金额(含税)", "amountUntax", "返佣金额(未税)");
                break;
            case OA_ACTUAL:
                addColumns(columns, "sourceSubType", "实际发生明细类型", "approvalState", "审批状态", "sourceCode", "审批编号",
                        "linkOaCode", "关联审批编号", "linkOaType", "关联审批类型", "budgetCategoryId", "归属预算分类",
                        "customerId", "项目所属客户", "supplierName", "项目所属供应商", "projectId", "归属项目", "leaderId", "归属员工",
                        "actualOwnerId", "实际花销人", "deptCode", "归属部门", "summary", "摘要", "amount", "实际发生金额",
                        "submitterId", "提交人", "applyTime", "提交时间", "approveTime", "完成时间", "accountDate", "入账日期");
                break;
            default:
                break;
        }
        return columns;
    }

    private void addColumns(List<ColumnVO> columns, String... definitions) {
        for (int i = 0; i < definitions.length; i += 2) {
            columns.add(new ColumnVO().setProp(definitions[i]).setLabel(definitions[i + 1]));
        }
    }

    private LambdaQueryWrapper<AnalysisFactBudget> buildBudgetQuery(QueryParams params) {
        return new LambdaQueryWrapper<AnalysisFactBudget>()
                .eq(AnalysisFactBudget::getDeleted, 0)
                .in(AnalysisFactBudget::getBizType, Arrays.asList(BudgetBizType.DEPT, BudgetBizType.PROJECT))
                .ge(AnalysisFactBudget::getStatMonth, DateUtils.truncate(params.getRangeStart(), Calendar.MONTH))
                .lt(AnalysisFactBudget::getStatMonth, DateUtils.addMonths(DateUtils.truncate(params.getRangeEnd(), Calendar.MONTH), 1))
                .in(CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactBudget::getDeptCode, params.getDeptCodes())
                .in(CollectionUtils.isNotEmpty(params.getAuthDeptCodes()), AnalysisFactBudget::getDeptCode, params.getAuthDeptCodes())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactBudget::getBudgetCategoryId, params.getBudgetCategoryIds());
    }

    private LambdaQueryWrapper<AnalysisFactActual> buildActualQuery(QueryParams params) {
        return new LambdaQueryWrapper<AnalysisFactActual>()
                .eq(AnalysisFactActual::getDeleted, 0)
                .ge(AnalysisFactActual::getStatMonth, DateUtils.truncate(params.getRangeStart(), Calendar.MONTH))
                .lt(AnalysisFactActual::getStatMonth, DateUtils.addMonths(DateUtils.truncate(params.getRangeEnd(), Calendar.MONTH), 1))
                .in(CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactActual::getDeptCode, params.getDeptCodes())
                .in(CollectionUtils.isNotEmpty(params.getAuthLeaderIds()), AnalysisFactActual::getLeaderId, params.getAuthLeaderIds())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactActual::getBudgetCategoryId, params.getBudgetCategoryIds());
    }

    private Map<Long, OaBudgetCategory> loadBudgetCategoryMap(List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts) throws BusinessException {
        List<Long> ids = new ArrayList<>();
        ids.addAll(budgetFacts.stream().map(AnalysisFactBudget::getBudgetCategoryId).filter(Objects::nonNull).collect(Collectors.toList()));
        ids.addAll(budgetFacts.stream().map(AnalysisFactBudget::getBudgetParentId).filter(Objects::nonNull).collect(Collectors.toList()));
        ids.addAll(actualFacts.stream().map(AnalysisFactActual::getBudgetCategoryId).filter(Objects::nonNull).collect(Collectors.toList()));
        ids.addAll(actualFacts.stream().map(AnalysisFactActual::getBudgetParentId).filter(Objects::nonNull).collect(Collectors.toList()));
        List<Long> distinctIds = ids.stream().distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(distinctIds)) {
            return Collections.emptyMap();
        }
        return budgetCategoryService.query(new BudgetCategoryService.QueryParams().setIds(distinctIds)).getRecords().stream()
                .collect(Collectors.toMap(OaBudgetCategory::getId, Function.identity(), (a, b) -> a));
    }

    private QueryParams normalize(QueryParams params) {
        QueryParams safeParams = params == null ? new QueryParams() : params;
        if (safeParams.getTimeGranularity() == null) {
            safeParams.setTimeGranularity(TimeGranularity.MONTH);
        }
        if (safeParams.getAmountType() == null) {
            safeParams.setAmountType(AmountType.TAX);
        }
        Date currentMonth = DateUtils.truncate(new Date(), Calendar.MONTH);
        if (safeParams.getRangeStart() == null) {
            safeParams.setRangeStart(currentMonth);
        }
        if (safeParams.getRangeEnd() == null) {
            safeParams.setRangeEnd(safeParams.getRangeStart());
        }
        if (StringUtils.isBlank(safeParams.getDisplayMode())) {
            safeParams.setDisplayMode("BY_DEPT");
        }
        return safeParams;
    }

    private List<String> resolveDeptCodes(QueryParams params, List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts) {
        if (CollectionUtils.isNotEmpty(params.getDeptCodes())) {
            return params.getDeptCodes();
        }
        if (CollectionUtils.isNotEmpty(params.getAuthDeptCodes())) {
            return params.getAuthDeptCodes();
        }
        List<String> deptCodes = new ArrayList<>();
        deptCodes.addAll(budgetFacts.stream().map(AnalysisFactBudget::getDeptCode).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        deptCodes.addAll(actualFacts.stream().map(AnalysisFactActual::getDeptCode).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        return deptCodes.stream().distinct().sorted().collect(Collectors.toList());
    }

    private List<String> buildTimeKeys(TimeGranularity granularity, Date rangeStart, Date rangeEnd) {
        List<String> keys = new ArrayList<>();
        Calendar start = Calendar.getInstance();
        start.setTime(DateUtils.truncate(rangeStart, Calendar.MONTH));
        Calendar end = Calendar.getInstance();
        end.setTime(DateUtils.truncate(rangeEnd, Calendar.MONTH));
        switch (granularity) {
            case YEAR:
                keys.add("Y1");
                break;
            case HALF_YEAR:
                keys.add("H1");
                keys.add("H2");
                break;
            case QUARTER:
                keys.add("Q1");
                keys.add("Q2");
                keys.add("Q3");
                keys.add("Q4");
                break;
            case MONTH:
            default:
                while (!start.after(end)) {
                    keys.add("M" + (start.get(Calendar.MONTH) + 1));
                    start.add(Calendar.MONTH, 1);
                }
                break;
        }
        return keys;
    }

    private BigDecimal sumBudget(List<AnalysisFactBudget> facts, String timeKey, boolean begin, TimeGranularity granularity) {
        return facts.stream()
                .filter(f -> timeKey.equals(resolveTimeKey(f.getStatMonth(), granularity)))
                .map(f -> begin ? safeValue(f.getBudgetAmountBegin()) : safeValue(f.getBudgetAmountEnd()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumActual(List<AnalysisFactActual> facts, String timeKey, AmountType amountType, TimeGranularity granularity) {
        return facts.stream()
                .filter(f -> timeKey.equals(resolveTimeKey(f.getStatMonth(), granularity)))
                .map(f -> AmountType.UNTAX == amountType ? safeValue(f.getAmountUntax()) : safeValue(f.getAmountTax()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveTimeKey(Date statMonth, TimeGranularity granularity) {
        if (statMonth == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(statMonth);
        int month = calendar.get(Calendar.MONTH) + 1;
        if (granularity == TimeGranularity.YEAR) {
            return "Y1";
        }
        if (granularity == TimeGranularity.HALF_YEAR) {
            return month <= 6 ? "H1" : "H2";
        }
        if (granularity == TimeGranularity.QUARTER) {
            return "Q" + ((month - 1) / 3 + 1);
        }
        return "M" + month;
    }

    private boolean isInGroup(OaCategoryGroup group, Long parentId, Long categoryId, Map<Long, OaBudgetCategory> categoryMap) {
        OaBudgetCategory category = parentId == null ? categoryMap.get(categoryId) : categoryMap.get(parentId);
        if (category == null || StringUtils.isBlank(category.getCode())) {
            return false;
        }
        String code = category.getCode();
        switch (group) {
            case INCOME:
                return code.startsWith("10");
            case COST:
                return code.startsWith("11");
            case FEE:
                return code.startsWith("12");
            default:
                return false;
        }
    }

    private BigDecimal calcRate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || BigDecimal.ZERO.compareTo(denominator) == 0) {
            return null;
        }
        return safeValue(numerator).divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal safeValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToTotal(String value) {
        return StringUtils.isBlank(value) ? "__TOTAL__" : value;
    }

    private List<Long> defaultBudgetCategoryIds(List<Long> budgetCategoryIds) {
        return CollectionUtils.isEmpty(budgetCategoryIds) ? Collections.emptyList() : budgetCategoryIds;
    }

    private Date resolveBucketStart(TimeGranularity granularity, Date rangeStart, String timeUnitKey) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.truncate(rangeStart == null ? new Date() : rangeStart, Calendar.MONTH));
        if (StringUtils.isBlank(timeUnitKey)) {
            return calendar.getTime();
        }
        switch (granularity) {
            case YEAR:
                calendar.set(Calendar.MONTH, 0);
                break;
            case HALF_YEAR:
                calendar.set(Calendar.MONTH, "H2".equals(timeUnitKey) ? 6 : 0);
                break;
            case QUARTER:
                calendar.set(Calendar.MONTH, (parseSuffix(timeUnitKey) - 1) * 3);
                break;
            case MONTH:
            default:
                calendar.set(Calendar.MONTH, parseSuffix(timeUnitKey) - 1);
                break;
        }
        return calendar.getTime();
    }

    private Date resolveBucketEndExclusive(TimeGranularity granularity, Date rangeStart, String timeUnitKey) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(resolveBucketStart(granularity, rangeStart, timeUnitKey));
        switch (granularity) {
            case YEAR:
                calendar.add(Calendar.YEAR, 1);
                break;
            case HALF_YEAR:
                calendar.add(Calendar.MONTH, 6);
                break;
            case QUARTER:
                calendar.add(Calendar.MONTH, 3);
                break;
            case MONTH:
            default:
                calendar.add(Calendar.MONTH, 1);
                break;
        }
        return calendar.getTime();
    }

    private int parseSuffix(String timeUnitKey) {
        return Integer.parseInt(timeUnitKey.substring(1));
    }

    private enum RowKind {
        PARENT,
        CHILD,
        CALC
    }

    private enum OaCategoryGroup {
        INCOME,
        COST,
        FEE
    }

    private static class RowDefinition {
        private final String key;
        private final String name;
        private final OaCategoryGroup group;
        private final RowKind kind;

        private RowDefinition(String key, String name, OaCategoryGroup group, RowKind kind) {
            this.key = key;
            this.name = name;
            this.group = group;
            this.kind = kind;
        }

        static RowDefinition parent(String key, String name, OaCategoryGroup group) {
            return new RowDefinition(key, name, group, RowKind.PARENT);
        }

        static RowDefinition child(String key, OaCategoryGroup group) {
            return new RowDefinition(key, null, group, RowKind.CHILD);
        }

        static RowDefinition calc(String key, String name) {
            return new RowDefinition(key, name, null, RowKind.CALC);
        }
    }
}
