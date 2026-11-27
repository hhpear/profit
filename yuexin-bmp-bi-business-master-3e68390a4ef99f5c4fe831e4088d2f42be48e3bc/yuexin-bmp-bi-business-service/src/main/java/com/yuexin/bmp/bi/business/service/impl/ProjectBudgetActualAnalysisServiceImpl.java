package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.AnalysisFactActualRepo;
import com.yuexin.bmp.bi.business.repo.AnalysisFactBudgetRepo;
import com.yuexin.bmp.bi.common.*;
import com.yuexin.bmp.bi.service.ProjectBudgetActualAnalysisService;
import com.yuexin.bmp.finance.business.BudgetCategoryService;
import com.yuexin.bmp.finance.model.OaBudgetCategory;
import com.yuexin.bmp.finance.model.ProjectLabel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.DubboReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class ProjectBudgetActualAnalysisServiceImpl implements ProjectBudgetActualAnalysisService {

    private final AnalysisFactBudgetRepo analysisFactBudgetRepo;
    private final AnalysisFactActualRepo analysisFactActualRepo;
    @DubboReference
    private BudgetCategoryService budgetCategoryService;

    @Override
    public List<QueryVO> query(QueryParams params) throws BusinessException {
        QueryParams safeParams = normalize(params);
        List<AnalysisFactBudget> budgetFacts = analysisFactBudgetRepo.selectList(buildBudgetQuery(safeParams));
        List<AnalysisFactActual> actualFacts = analysisFactActualRepo.selectList(buildActualQuery(safeParams));
        if (CollectionUtils.isEmpty(budgetFacts) && CollectionUtils.isEmpty(actualFacts)) {
            return Collections.emptyList();
        }
        List<String> timeKeys = buildTimeKeys(safeParams.getTimeGranularity(), safeParams.getRangeStart(), safeParams.getRangeEnd());
        Map<Long, OaBudgetCategory> categoryMap = loadBudgetCategoryMap(budgetFacts, actualFacts);
        Map<RowKey, List<AnalysisFactBudget>> budgetGroup = budgetFacts.stream()
                .collect(Collectors.groupingBy(f -> RowKey.fromBudgetEntity(f, safeParams.getGroupFields()), LinkedHashMap::new, Collectors.toList()));
        Map<RowKey, List<AnalysisFactActual>> actualGroup = actualFacts.stream()
                .collect(Collectors.groupingBy(f -> RowKey.fromActualEntity(f, safeParams.getGroupFields()), LinkedHashMap::new, Collectors.toList()));
        Set<RowKey> entityKeys = new LinkedHashSet<>();
        entityKeys.addAll(budgetGroup.keySet());
        entityKeys.addAll(actualGroup.keySet());
        return entityKeys.stream()
                .sorted(Comparator.comparing(this::buildEntitySortKey))
                .flatMap(key -> buildFixedRows(key, timeKeys,
                        budgetGroup.getOrDefault(key, Collections.emptyList()),
                        actualGroup.getOrDefault(key, Collections.emptyList()), safeParams, categoryMap).stream())
                .collect(Collectors.toList());
    }

    @Override
    public List<StatVO> queryStat(QueryParams params) throws BusinessException {
        QueryParams safeParams = normalize(params);
        List<AnalysisFactActual> facts = analysisFactActualRepo.selectList(buildActualQuery(safeParams));
        Map<Long, OaBudgetCategory> categoryMap = loadBudgetCategoryMap(Collections.emptyList(), facts);
        Map<String, List<AnalysisFactActual>> factMap = new LinkedHashMap<>();
        for (AnalysisFactActual fact : facts) {
            String key = blank(fact.getDeptCode()) + "#" + blank(fact.getIncomeType());
            factMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fact);
        }
        return factMap.entrySet().stream().map(entry -> {
            String[] values = entry.getKey().split("#", -1);
            BigDecimal income = sumActual(entry.getValue(), "income", null, safeParams, categoryMap);
            BigDecimal cost = sumActual(entry.getValue(), "cost_total", null, safeParams, categoryMap);
            BigDecimal fee = sumActual(entry.getValue(), "daily_fee_total", null, safeParams, categoryMap);
            return new StatVO()
                    .setDeptCode(emptyToNull(values[0]))
                    .setIncomeType(StringUtils.isBlank(values[1]) ? null : Long.valueOf(values[1]))
                    .setActualAmount(income.subtract(cost).subtract(fee))
                    .setIncomeAmount(income)
                    .setCostAmount(cost)
                    .setGrossProfitAmount(income.subtract(cost))
                    .setDailyFeeAmount(fee)
                    .setNetProfitAmount(income.subtract(cost).subtract(fee));
        }).sorted(Comparator.comparing(this::buildSortKey)).collect(Collectors.toList());
    }

    private List<QueryVO> buildFixedRows(RowKey key, List<String> timeKeys, List<AnalysisFactBudget> budgetFacts,
                                         List<AnalysisFactActual> actualFacts, QueryParams params,
                                         Map<Long, OaBudgetCategory> categoryMap) {
        List<QueryVO> rows = new ArrayList<>();
        rows.add(buildDetailRow(key, "income", "收入", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildDetailRow(key, "channel_fee", "通道费", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildDetailRow(key, "rebate", "返佣", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildDetailRow(key, "other_cost", "其他成本", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildSumRow(key, "cost_total", "成本小计", timeKeys, rows.subList(1, 4)));
        rows.add(buildSubtractRow(key, "gross_profit", "毛利", timeKeys, rows.get(0), rows.get(4)));
        rows.add(buildRateRow(key, "gross_profit_rate", "毛利率", timeKeys, rows.get(5), rows.get(0)));
        rows.add(buildDetailRow(key, "business_entertainment", "业务招待费", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildDetailRow(key, "travel_expense", "差旅费", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildDetailRow(key, "bid_service", "投标服务费", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildDetailRow(key, "other_expense", "其他费用", timeKeys, budgetFacts, actualFacts, params, categoryMap));
        rows.add(buildSumRow(key, "daily_fee_total", "日常费用小计", timeKeys, rows.subList(7, 11)));
        rows.add(buildSubtractRow(key, "net_profit", "净利", timeKeys, rows.get(5), rows.get(11)));
        rows.add(buildRateRow(key, "net_profit_rate", "净利率", timeKeys, rows.get(12), rows.get(0)));
        return rows;
    }

    private QueryVO buildDetailRow(RowKey key, String rowKey, String rowName, List<String> timeKeys,
                                   List<AnalysisFactBudget> budgetFacts, List<AnalysisFactActual> actualFacts,
                                   QueryParams params, Map<Long, OaBudgetCategory> categoryMap) {
        List<AnalysisFactBudget> matchedBudgets = budgetFacts.stream()
                .filter(f -> matchesCategory(rowKey, f.getBudgetCategoryId(), f.getBudgetParentId(), categoryMap)).collect(Collectors.toList());
        List<AnalysisFactActual> matchedActuals = actualFacts.stream()
                .filter(f -> matchesCategory(rowKey, f.getBudgetCategoryId(), f.getBudgetParentId(), categoryMap)).collect(Collectors.toList());
        List<Long> categoryIds = new ArrayList<>();
        categoryIds.addAll(matchedBudgets.stream().map(AnalysisFactBudget::getBudgetCategoryId).filter(Objects::nonNull).collect(Collectors.toList()));
        categoryIds.addAll(matchedActuals.stream().map(AnalysisFactActual::getBudgetCategoryId).filter(Objects::nonNull).collect(Collectors.toList()));
        categoryIds = categoryIds.stream().distinct().collect(Collectors.toList());
        QueryVO row = baseRow(key, rowKey, rowName, "DETAIL").setBudgetCategoryIds(categoryIds)
                .setBudgetCategoryId(categoryIds.size() == 1 ? categoryIds.get(0) : null);
        for (String timeKey : timeKeys) {
            BigDecimal begin = sumBudget(matchedBudgets, timeKey, true, params.getTimeGranularity());
            BigDecimal end = sumBudget(matchedBudgets, timeKey, false, params.getTimeGranularity());
            BigDecimal actual = sumActual(matchedActuals, timeKey, params.getAmountType(), params.getTimeGranularity());
            row.getCells().put(timeKey, metric(begin, end, actual));
        }
        return row;
    }

    private QueryVO buildSumRow(RowKey key, String rowKey, String rowName, List<String> timeKeys, List<QueryVO> sourceRows) {
        QueryVO row = baseRow(key, rowKey, rowName, "SUMMARY").setBudgetCategoryIds(sourceRows.stream()
                .flatMap(item -> item.getBudgetCategoryIds().stream()).distinct().collect(Collectors.toList()));
        for (String timeKey : timeKeys) {
            row.getCells().put(timeKey, sumCells(sourceRows, timeKey));
        }
        return row;
    }

    private QueryVO buildSubtractRow(RowKey key, String rowKey, String rowName, List<String> timeKeys, QueryVO left, QueryVO right) {
        QueryVO row = baseRow(key, rowKey, rowName, "CALC");
        for (String timeKey : timeKeys) {
            MetricCellVO a = left.getCells().get(timeKey);
            MetricCellVO b = right.getCells().get(timeKey);
            row.getCells().put(timeKey, metric(safeValue(a.getBeginBudget()).subtract(safeValue(b.getBeginBudget())),
                    safeValue(a.getEndBudget()).subtract(safeValue(b.getEndBudget())),
                    safeValue(a.getActualAmount()).subtract(safeValue(b.getActualAmount()))));
        }
        return row;
    }

    private QueryVO buildRateRow(RowKey key, String rowKey, String rowName, List<String> timeKeys, QueryVO numerator, QueryVO denominator) {
        QueryVO row = baseRow(key, rowKey, rowName, "CALC");
        for (String timeKey : timeKeys) {
            MetricCellVO a = numerator.getCells().get(timeKey);
            MetricCellVO b = denominator.getCells().get(timeKey);
            row.getCells().put(timeKey, new MetricCellVO()
                    .setBeginBudget(calcRate(a.getBeginBudget(), b.getBeginBudget()))
                    .setEndBudget(calcRate(a.getEndBudget(), b.getEndBudget()))
                    .setActualAmount(calcRate(a.getActualAmount(), b.getActualAmount())));
        }
        return row;
    }

    private QueryVO baseRow(RowKey key, String rowKey, String rowName, String rowType) {
        return new QueryVO().setDeptCode(key.deptCode).setIncomeType(key.incomeType).setCustomerId(key.customerId)
                .setProjectId(key.projectId).setProjectPoolKey(key.projectPoolKey).setLeaderId(key.leaderId)
                .setProjectLabel(key.projectLabel).setRowType(rowType).setRowKey(rowKey).setRowName(rowName)
                .setBudgetCategoryIds(new ArrayList<>()).setCells(new LinkedHashMap<>());
    }

    private MetricCellVO metric(BigDecimal begin, BigDecimal end, BigDecimal actual) {
        return new MetricCellVO().setBeginBudget(begin).setEndBudget(end).setActualAmount(actual)
                .setDiffAmount(end.subtract(actual)).setReachRate(calcRate(actual, end));
    }

    private MetricCellVO sumCells(List<QueryVO> rows, String timeKey) {
        BigDecimal begin = BigDecimal.ZERO;
        BigDecimal end = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        for (QueryVO row : rows) {
            MetricCellVO cell = row.getCells().get(timeKey);
            begin = begin.add(safeValue(cell.getBeginBudget()));
            end = end.add(safeValue(cell.getEndBudget()));
            actual = actual.add(safeValue(cell.getActualAmount()));
        }
        return metric(begin, end, actual);
    }

    private boolean matchesCategory(String rowKey, Long categoryId, Long parentId, Map<Long, OaBudgetCategory> categoryMap) {
        OaBudgetCategory category = categoryMap.get(categoryId);
        OaBudgetCategory parent = categoryMap.get(parentId);
        String name = category == null ? "" : StringUtils.defaultString(category.getName());
        String code = parent != null ? StringUtils.defaultString(parent.getCode())
                : category == null ? "" : StringUtils.defaultString(category.getCode());
        boolean income = code.startsWith("10");
        boolean cost = code.startsWith("11");
        boolean fee = code.startsWith("12");
        boolean channel = name.contains("通道");
        boolean rebate = name.contains("返佣");
        boolean entertainment = name.contains("业务招待") || name.contains("招待费");
        boolean travel = name.contains("差旅");
        boolean bid = name.contains("投标服务");
        switch (rowKey) {
            case "income": return income;
            case "channel_fee": return cost && channel;
            case "rebate": return cost && rebate;
            case "other_cost": return cost && !channel && !rebate;
            case "cost_total": return cost;
            case "business_entertainment": return fee && entertainment;
            case "travel_expense": return fee && travel;
            case "bid_service": return fee && bid;
            case "other_expense": return fee && !entertainment && !travel && !bid;
            case "daily_fee_total": return fee;
            default: return false;
        }
    }

    private BigDecimal sumActual(List<AnalysisFactActual> facts, String rowKey, String timeKey, QueryParams params,
                                 Map<Long, OaBudgetCategory> categoryMap) {
        return facts.stream().filter(f -> matchesCategory(rowKey, f.getBudgetCategoryId(), f.getBudgetParentId(), categoryMap))
                .filter(f -> timeKey == null || timeKey.equals(resolveTimeKey(f.getStatMonth(), params.getTimeGranularity())))
                .map(f -> resolveActualAmount(f, params.getAmountType())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumBudget(List<AnalysisFactBudget> facts, String timeKey, boolean begin,
                                 TimeGranularity granularity) {
        return facts.stream().filter(f -> timeKey.equals(resolveTimeKey(f.getStatMonth(), granularity)))
                .map(f -> begin ? safeValue(f.getBudgetAmountBegin()) : safeValue(f.getBudgetAmountEnd()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumActual(List<AnalysisFactActual> facts, String timeKey, AmountType amountType,
                                 TimeGranularity granularity) {
        return facts.stream().filter(f -> timeKey.equals(resolveTimeKey(f.getStatMonth(), granularity)))
                .map(f -> resolveActualAmount(f, amountType)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, OaBudgetCategory> loadBudgetCategoryMap(List<AnalysisFactBudget> budgets,
                                                              List<AnalysisFactActual> actuals) throws BusinessException {
        List<Long> ids = new ArrayList<>();
        for (AnalysisFactBudget fact : budgets) {
            ids.add(fact.getBudgetCategoryId());
            ids.add(fact.getBudgetParentId());
        }
        for (AnalysisFactActual fact : actuals) {
            ids.add(fact.getBudgetCategoryId());
            ids.add(fact.getBudgetParentId());
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return budgetCategoryService.query(new BudgetCategoryService.QueryParams().setIds(distinctIds)).getRecords().stream()
                .collect(Collectors.toMap(OaBudgetCategory::getId, Function.identity(), (a, b) -> a));
    }

    private String buildEntitySortKey(RowKey row) {
        return blank(row.deptCode) + "#" + blank(row.incomeType) + "#" + blank(row.customerId) + "#"
                + blank(row.projectPoolKey) + "#" + blank(row.projectId) + "#" + blank(row.leaderId);
    }

    @Override
    public BudgetDrilldownVO drilldownBudget(BudgetDrilldownParams params) throws BusinessException {
        Date monthStart = resolveBucketStart(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        Date monthEndExclusive = resolveBucketEndExclusive(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        List<AnalysisFactBudget> facts = analysisFactBudgetRepo.selectList(buildBudgetDrilldownQuery(params, monthStart, monthEndExclusive));
        return new BudgetDrilldownVO()
                .setDeptCodes(resolveRange(params.getDeptCode(), params.getDeptCodes()))
                .setIncomeTypes(resolveRange(params.getIncomeType(), params.getIncomeTypes()))
                .setCustomerIds(resolveRange(params.getCustomerId(), params.getCustomerIds()))
                .setProjectIds(resolveRange(params.getProjectId(), params.getProjectIds()))
                .setProjectPoolProjectIds(facts.stream().map(AnalysisFactBudget::getProjectId).filter(Objects::nonNull).distinct().collect(Collectors.toList()))
                .setLeaderIds(resolveRange(params.getLeaderId(), params.getLeaderIds()))
                .setProjectLabels(StringUtils.isNotBlank(params.getProjectLabel()) ? singletonList(params.getProjectLabel())
                        : CollectionUtils.isNotEmpty(params.getProjectLabels()) ? params.getProjectLabels()
                        : resolveProjectLabels(null, facts.stream().map(AnalysisFactBudget::getProjectLabel).collect(Collectors.toList())))
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
        List<AnalysisFactActual> facts = analysisFactActualRepo.selectList(buildActualDrilldownQuery(params, monthStart, monthEndExclusive));
        List<DrilldownTabVO> tabs = new ArrayList<>();
        for (AnalysisSourceType sourceType : AnalysisSourceType.values()) {
            List<AnalysisFactActual> sourceFacts = facts.stream()
                    .filter(fact -> sourceType == fact.getSourceType())
                    .collect(Collectors.toList());
            tabs.add(buildActualTab(sourceType, sourceFacts, params.getAmountType()));
        }
        return new ActualDrilldownVO().setTabs(tabs);
    }

    private void mergeBudgetFact(Map<RowKey, QueryVO> rowMap, RowKey rowKey, List<String> timeKeys,
                                 TimeGranularity granularity, AnalysisFactBudget fact) {
        QueryVO row = rowMap.computeIfAbsent(rowKey, key -> new QueryVO()
                .setDeptCode(key.deptCode)
                .setIncomeType(key.incomeType)
                .setCustomerId(key.customerId)
                .setProjectId(key.projectId)
                .setProjectPoolKey(key.projectPoolKey)
                .setLeaderId(key.leaderId)
                .setRowType(key.rowType)
                .setProjectLabel(key.projectLabel)
                .setBudgetCategoryId(key.budgetCategoryId)
                .setCells(initCells(timeKeys)));
        MetricCellVO cell = row.getCells().get(resolveTimeKey(fact.getStatMonth(), granularity));
        if (cell == null) {
            return;
        }
        cell.setBeginBudget(safeValue(cell.getBeginBudget()).add(safeValue(fact.getBudgetAmountBegin())));
        cell.setEndBudget(safeValue(cell.getEndBudget()).add(safeValue(fact.getBudgetAmountEnd())));
    }

    private void mergeActualFact(Map<RowKey, QueryVO> rowMap, RowKey rowKey, List<String> timeKeys,
                                 TimeGranularity granularity, AmountType amountType, AnalysisFactActual fact) {
        QueryVO row = rowMap.computeIfAbsent(rowKey, key -> new QueryVO()
                .setDeptCode(key.deptCode)
                .setIncomeType(key.incomeType)
                .setCustomerId(key.customerId)
                .setProjectId(key.projectId)
                .setProjectPoolKey(key.projectPoolKey)
                .setLeaderId(key.leaderId)
                .setRowType(key.rowType)
                .setProjectLabel(key.projectLabel)
                .setBudgetCategoryId(key.budgetCategoryId)
                .setCells(initCells(timeKeys)));
        MetricCellVO cell = row.getCells().get(resolveTimeKey(fact.getStatMonth(), granularity));
        if (cell == null) {
            return;
        }
        cell.setActualAmount(safeValue(cell.getActualAmount()).add(resolveActualAmount(fact, amountType)));
    }

    private void fillDerivedMetrics(QueryVO row) {
        row.getCells().values().forEach(cell -> {
            BigDecimal endBudget = safeValue(cell.getEndBudget());
            BigDecimal actualAmount = safeValue(cell.getActualAmount());
            cell.setDiffAmount(endBudget.subtract(actualAmount));
            cell.setReachRate(calcRate(actualAmount, endBudget));
        });
    }

    private Map<String, MetricCellVO> initCells(List<String> timeKeys) {
        Map<String, MetricCellVO> cells = new LinkedHashMap<>();
        timeKeys.forEach(timeKey -> cells.put(timeKey, new MetricCellVO()
                .setBeginBudget(BigDecimal.ZERO)
                .setEndBudget(BigDecimal.ZERO)
                .setActualAmount(BigDecimal.ZERO)
                .setDiffAmount(BigDecimal.ZERO)));
        return cells;
    }

    private LambdaQueryWrapper<AnalysisFactBudget> buildBudgetQuery(QueryParams params) {
        return new LambdaQueryWrapper<AnalysisFactBudget>()
                .eq(AnalysisFactBudget::getDeleted, 0)
                .eq(AnalysisFactBudget::getBizType, BudgetBizType.PROJECT)
                .ge(AnalysisFactBudget::getStatMonth, DateUtils.truncate(params.getRangeStart(), Calendar.MONTH))
                .lt(AnalysisFactBudget::getStatMonth, DateUtils.addMonths(DateUtils.truncate(params.getRangeEnd(), Calendar.MONTH), 1))
                .in(CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactBudget::getDeptCode, params.getDeptCodes())
                .in(CollectionUtils.isNotEmpty(params.getAuthDeptCodes()), AnalysisFactBudget::getDeptCode, params.getAuthDeptCodes())
                .in(CollectionUtils.isNotEmpty(params.getIncomeTypes()), AnalysisFactBudget::getIncomeType, params.getIncomeTypes())
                .in(CollectionUtils.isNotEmpty(params.getCustomerIds()), AnalysisFactBudget::getCustomerId, params.getCustomerIds())
                .in(CollectionUtils.isNotEmpty(params.getProjectIds()), AnalysisFactBudget::getProjectId, params.getProjectIds())
                .in(CollectionUtils.isNotEmpty(params.getLeaderIds()), AnalysisFactBudget::getLeaderId, params.getLeaderIds())
                .in(CollectionUtils.isNotEmpty(params.getProjectLabels()), AnalysisFactBudget::getProjectLabel, params.getProjectLabels())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactBudget::getBudgetCategoryId, params.getBudgetCategoryIds());
    }

    private LambdaQueryWrapper<AnalysisFactActual> buildActualQuery(QueryParams params) {
        return new LambdaQueryWrapper<AnalysisFactActual>()
                .eq(AnalysisFactActual::getDeleted, 0)
                .ge(AnalysisFactActual::getStatMonth, DateUtils.truncate(params.getRangeStart(), Calendar.MONTH))
                .lt(AnalysisFactActual::getStatMonth, DateUtils.addMonths(DateUtils.truncate(params.getRangeEnd(), Calendar.MONTH), 1))
                .in(CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactActual::getDeptCode, params.getDeptCodes())
                .in(CollectionUtils.isNotEmpty(params.getIncomeTypes()), AnalysisFactActual::getIncomeType, params.getIncomeTypes())
                .in(CollectionUtils.isNotEmpty(params.getCustomerIds()), AnalysisFactActual::getCustomerId, params.getCustomerIds())
                .in(CollectionUtils.isNotEmpty(params.getProjectIds()), AnalysisFactActual::getProjectId, params.getProjectIds())
                .in(CollectionUtils.isNotEmpty(params.getLeaderIds()), AnalysisFactActual::getLeaderId, params.getLeaderIds())
                .in(CollectionUtils.isNotEmpty(params.getProjectLabels()), AnalysisFactActual::getProjectLabel, params.getProjectLabels())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactActual::getBudgetCategoryId, params.getBudgetCategoryIds())
                .in(CollectionUtils.isNotEmpty(params.getAuthLeaderIds()), AnalysisFactActual::getLeaderId, params.getAuthLeaderIds());
    }

    private LambdaQueryWrapper<AnalysisFactBudget> buildBudgetDrilldownQuery(BudgetDrilldownParams params, Date monthStart, Date monthEndExclusive) {
        return new LambdaQueryWrapper<AnalysisFactBudget>()
                .eq(AnalysisFactBudget::getDeleted, 0)
                .eq(AnalysisFactBudget::getBizType, BudgetBizType.PROJECT)
                .ge(AnalysisFactBudget::getStatMonth, monthStart)
                .lt(AnalysisFactBudget::getStatMonth, monthEndExclusive)
                .eq(StringUtils.isNotBlank(params.getDeptCode()), AnalysisFactBudget::getDeptCode, params.getDeptCode())
                .in(StringUtils.isBlank(params.getDeptCode()) && CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactBudget::getDeptCode, params.getDeptCodes())
                .eq(params.getIncomeType() != null, AnalysisFactBudget::getIncomeType, params.getIncomeType())
                .in(params.getIncomeType() == null && CollectionUtils.isNotEmpty(params.getIncomeTypes()), AnalysisFactBudget::getIncomeType, params.getIncomeTypes())
                .eq(params.getCustomerId() != null, AnalysisFactBudget::getCustomerId, params.getCustomerId())
                .in(params.getCustomerId() == null && CollectionUtils.isNotEmpty(params.getCustomerIds()), AnalysisFactBudget::getCustomerId, params.getCustomerIds())
                .eq(params.getProjectId() != null, AnalysisFactBudget::getProjectId, params.getProjectId())
                .in(params.getProjectId() == null && CollectionUtils.isNotEmpty(params.getProjectIds()), AnalysisFactBudget::getProjectId, params.getProjectIds())
                .eq(StringUtils.isNotBlank(params.getProjectPoolKey()), AnalysisFactBudget::getProjectPoolKey, params.getProjectPoolKey())
                .eq(params.getLeaderId() != null, AnalysisFactBudget::getLeaderId, params.getLeaderId())
                .in(params.getLeaderId() == null && CollectionUtils.isNotEmpty(params.getLeaderIds()), AnalysisFactBudget::getLeaderId, params.getLeaderIds())
                .eq(StringUtils.isNotBlank(params.getProjectLabel()), AnalysisFactBudget::getProjectLabel, params.getProjectLabel())
                .in(StringUtils.isBlank(params.getProjectLabel()) && CollectionUtils.isNotEmpty(params.getProjectLabels()), AnalysisFactBudget::getProjectLabel, params.getProjectLabels())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactBudget::getBudgetCategoryId, params.getBudgetCategoryIds())
                .in(CollectionUtils.isNotEmpty(params.getAuthDeptCodes()), AnalysisFactBudget::getDeptCode, params.getAuthDeptCodes());
    }

    private LambdaQueryWrapper<AnalysisFactActual> buildActualDrilldownQuery(ActualDrilldownParams params, Date monthStart, Date monthEndExclusive) {
        return new LambdaQueryWrapper<AnalysisFactActual>()
                .eq(AnalysisFactActual::getDeleted, 0)
                .ge(AnalysisFactActual::getStatMonth, monthStart)
                .lt(AnalysisFactActual::getStatMonth, monthEndExclusive)
                .eq(StringUtils.isNotBlank(params.getDeptCode()), AnalysisFactActual::getDeptCode, params.getDeptCode())
                .in(StringUtils.isBlank(params.getDeptCode()) && CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactActual::getDeptCode, params.getDeptCodes())
                .eq(params.getIncomeType() != null, AnalysisFactActual::getIncomeType, params.getIncomeType())
                .in(params.getIncomeType() == null && CollectionUtils.isNotEmpty(params.getIncomeTypes()), AnalysisFactActual::getIncomeType, params.getIncomeTypes())
                .eq(params.getCustomerId() != null, AnalysisFactActual::getCustomerId, params.getCustomerId())
                .in(params.getCustomerId() == null && CollectionUtils.isNotEmpty(params.getCustomerIds()), AnalysisFactActual::getCustomerId, params.getCustomerIds())
                .eq(params.getProjectId() != null, AnalysisFactActual::getProjectId, params.getProjectId())
                .in(params.getProjectId() == null && CollectionUtils.isNotEmpty(params.getProjectIds()), AnalysisFactActual::getProjectId, params.getProjectIds())
                .eq(StringUtils.isNotBlank(params.getProjectPoolKey()), AnalysisFactActual::getProjectPoolKey, params.getProjectPoolKey())
                .eq(params.getLeaderId() != null, AnalysisFactActual::getLeaderId, params.getLeaderId())
                .in(params.getLeaderId() == null && CollectionUtils.isNotEmpty(params.getLeaderIds()), AnalysisFactActual::getLeaderId, params.getLeaderIds())
                .eq(StringUtils.isNotBlank(params.getProjectLabel()), AnalysisFactActual::getProjectLabel, params.getProjectLabel())
                .in(StringUtils.isBlank(params.getProjectLabel()) && CollectionUtils.isNotEmpty(params.getProjectLabels()), AnalysisFactActual::getProjectLabel, params.getProjectLabels())
                .in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactActual::getBudgetCategoryId, params.getBudgetCategoryIds())
                .in(CollectionUtils.isNotEmpty(params.getAuthLeaderIds()), AnalysisFactActual::getLeaderId, params.getAuthLeaderIds());
    }

    private DrilldownTabVO buildActualTab(AnalysisSourceType sourceType, List<AnalysisFactActual> sourceFacts, AmountType amountType) {
        List<ColumnVO> columns = buildActualColumns(sourceType);
        List<Map<String, Object>> rows = sourceFacts.stream().map(fact -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("statMonth", fact.getStatMonth());
            row.put("sourceCode", fact.getSourceCode());
            row.put("deptCode", fact.getDeptCode());
            row.put("incomeType", fact.getIncomeType());
            row.put("customerId", fact.getCustomerId());
            row.put("projectId", fact.getProjectId());
            row.put("leaderId", fact.getLeaderId());
            row.put("budgetCategoryId", fact.getBudgetCategoryId());
            row.put("approvalState", fact.getApprovalState());
            row.put("sourceSubType", fact.getSourceSubType());
            row.put("bizDate", fact.getBizDate());
            row.put("orderProperty", fact.getOrderProperty());
            row.put("orderState", fact.getOrderState());
            row.put("platform", fact.getPlatform());
            row.put("entCode", fact.getEntCode());
            row.put("entName", fact.getEntName());
            row.put("accountNumber", fact.getAccountNumber());
            row.put("feeCount", fact.getFeeCount());
            row.put("actualOwnerId", fact.getActualOwnerId());
            row.put("summary", fact.getSummary());
            row.put("submitterId", fact.getSubmitterId());
            row.put("applyTime", fact.getApplyTime());
            row.put("approveTime", fact.getApproveTime());
            row.put("accountDate", fact.getAccountDate());
            row.put("linkOaId", fact.getLinkOaId());
            row.put("linkOaType", fact.getLinkOaType());
            row.put("supplierName", fact.getSupplierName());
            row.put("approvalCode", fact.getApprovalCode());
            row.put("channelCode", fact.getChannelCode());
            row.put("channelName", fact.getChannelName());
            row.put("accountName", fact.getAccountName());
            row.put("firstAgentRule", fact.getFirstAgentRule());
            row.put("secondAgentRule", fact.getSecondAgentRule());
            row.put("thirdAgentRule", fact.getThirdAgentRule());
            row.put("linkOaCode", fact.getLinkOaCode());
            row.put("amountTax", safeValue(fact.getAmountTax()));
            row.put("amountUntax", safeValue(fact.getAmountUntax()));
            row.put("amount", resolveActualAmount(fact, amountType));
            return row;
        }).collect(Collectors.toList());
        return new DrilldownTabVO()
                .setSourceType(sourceType.name())
                .setSourceName(sourceType.getName())
                .setColumns(columns)
                .setRows(rows)
                .setSummaryCount((long) sourceFacts.size())
                .setSummaryAmount(sourceFacts.stream().map(fact -> resolveActualAmount(fact, amountType)).reduce(BigDecimal.ZERO, BigDecimal::add));
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
        if (safeParams.getGroupFields() == null) {
            safeParams.setGroupFields(Collections.emptyList());
        }
        return safeParams;
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

    private BigDecimal resolveActualAmount(AnalysisFactActual fact, AmountType amountType) {
        return AmountType.UNTAX == amountType ? safeValue(fact.getAmountUntax()) : safeValue(fact.getAmountTax());
    }

    private List<Long> defaultBudgetCategoryIds(List<Long> budgetCategoryIds) {
        return CollectionUtils.isEmpty(budgetCategoryIds) ? Collections.emptyList() : budgetCategoryIds;
    }

    private List<String> resolveProjectLabels(String projectLabel, List<String> fallbackLabels) {
        if (StringUtils.isNotBlank(projectLabel)) {
            return Collections.singletonList(projectLabel);
        }
        return fallbackLabels.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    private <T> List<T> singletonList(T value) {
        return value == null ? Collections.emptyList() : Collections.singletonList(value);
    }

    private <T> List<T> resolveRange(T rowValue, List<T> filterValues) {
        return rowValue != null ? Collections.singletonList(rowValue)
                : CollectionUtils.isEmpty(filterValues) ? Collections.emptyList() : new ArrayList<>(filterValues);
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

    private String buildSortKey(QueryVO row) {
        return blank(row.getDeptCode()) + "#"
                + blank(row.getIncomeType()) + "#"
                + blank(row.getCustomerId()) + "#"
                + blank(row.getProjectPoolKey()) + "#"
                + blank(row.getProjectId()) + "#"
                + blank(row.getLeaderId()) + "#"
                + blank(row.getBudgetCategoryId());
    }

    private String buildSortKey(StatVO row) {
        return blank(row.getDeptCode()) + "#" + blank(row.getIncomeType());
    }

    private String blank(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String emptyToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private int parseSuffix(String timeUnitKey) {
        return Integer.parseInt(timeUnitKey.substring(1));
    }

    private static class RowKey {
        private String deptCode;
        private Long incomeType;
        private Long customerId;
        private Long projectId;
        private String projectPoolKey;
        private Long leaderId;
        private String rowType;
        private String projectLabel;
        private Long budgetCategoryId;

        static RowKey fromBudget(AnalysisFactBudget fact, List<String> groupFields) {
            return build(groupFields, fact.getDeptCode(), fact.getIncomeType(), fact.getCustomerId(), fact.getProjectId(),
                    fact.getProjectPoolKey(), fact.getLeaderId(), fact.getProjectLabel(), fact.getBudgetCategoryId());
        }

        static RowKey fromActual(AnalysisFactActual fact, List<String> groupFields) {
            return build(groupFields, fact.getDeptCode(), fact.getIncomeType(), fact.getCustomerId(), fact.getProjectId(),
                    fact.getProjectPoolKey(), fact.getLeaderId(), fact.getProjectLabel(), fact.getBudgetCategoryId());
        }

        static RowKey fromBudgetEntity(AnalysisFactBudget fact, List<String> groupFields) {
            return build(groupFields, fact.getDeptCode(), fact.getIncomeType(), fact.getCustomerId(), fact.getProjectId(),
                    fact.getProjectPoolKey(), fact.getLeaderId(), fact.getProjectLabel(), null);
        }

        static RowKey fromActualEntity(AnalysisFactActual fact, List<String> groupFields) {
            return build(groupFields, fact.getDeptCode(), fact.getIncomeType(), fact.getCustomerId(), fact.getProjectId(),
                    fact.getProjectPoolKey(), fact.getLeaderId(), fact.getProjectLabel(), null);
        }

        private static RowKey build(List<String> groupFields, String deptCode, Long incomeType, Long customerId, Long projectId,
                                    String projectPoolKey, Long leaderId, String projectLabel, Long budgetCategoryId) {
            RowKey rowKey = new RowKey();
            rowKey.budgetCategoryId = budgetCategoryId;
            rowKey.deptCode = groupFields.contains("dept") ? deptCode : null;
            rowKey.incomeType = groupFields.contains("incomeType") ? incomeType : null;
            rowKey.customerId = groupFields.contains("customer") ? customerId : null;
            rowKey.leaderId = groupFields.contains("leader") ? leaderId : null;
            if (groupFields.contains("project")) {
                rowKey.projectLabel = projectLabel;
                if (isPool(projectLabel, projectPoolKey)) {
                    rowKey.rowType = "POOL";
                    rowKey.projectPoolKey = projectPoolKey;
                } else {
                    rowKey.rowType = "PROJECT";
                    rowKey.projectId = projectId;
                }
            }
            return rowKey;
        }

        private static boolean isPool(String projectLabel, String projectPoolKey) {
            return StringUtils.isNotBlank(projectPoolKey) && !ProjectLabel.Large.name().equals(projectLabel);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            RowKey rowKey = (RowKey) object;
            return Objects.equals(deptCode, rowKey.deptCode)
                    && Objects.equals(incomeType, rowKey.incomeType)
                    && Objects.equals(customerId, rowKey.customerId)
                    && Objects.equals(projectId, rowKey.projectId)
                    && Objects.equals(projectPoolKey, rowKey.projectPoolKey)
                    && Objects.equals(leaderId, rowKey.leaderId)
                    && Objects.equals(rowType, rowKey.rowType)
                    && Objects.equals(projectLabel, rowKey.projectLabel)
                    && Objects.equals(budgetCategoryId, rowKey.budgetCategoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deptCode, incomeType, customerId, projectId, projectPoolKey, leaderId, rowType, projectLabel, budgetCategoryId);
        }
    }
}
