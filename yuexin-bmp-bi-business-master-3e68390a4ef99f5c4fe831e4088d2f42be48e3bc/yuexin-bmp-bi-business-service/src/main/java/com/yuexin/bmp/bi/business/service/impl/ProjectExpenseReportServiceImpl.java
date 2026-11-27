package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.business.repo.AnalysisFactActualRepo;
import com.yuexin.bmp.bi.common.AnalysisFactActual;
import com.yuexin.bmp.bi.common.AnalysisSourceType;
import com.yuexin.bmp.bi.common.TimeGranularity;
import com.yuexin.bmp.bi.service.ProjectExpenseReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboService;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class ProjectExpenseReportServiceImpl implements ProjectExpenseReportService {

    private final AnalysisFactActualRepo analysisFactActualRepo;

    @Override
    public List<QueryVO> query(QueryParams params) throws BusinessException {
        QueryParams safeParams = normalize(params);
        List<AnalysisFactActual> facts = analysisFactActualRepo.selectList(buildQuery(safeParams));
        if (CollectionUtils.isEmpty(facts)) {
            return Collections.emptyList();
        }
        List<String> timeKeys = buildTimeKeys(safeParams.getTimeGranularity(), safeParams.getRangeStart(), safeParams.getRangeEnd());
        Map<RowKey, QueryVO> resultMap = new LinkedHashMap<>();
        for (AnalysisFactActual fact : facts) {
            RowKey rowKey = RowKey.fromFact(fact, safeParams.getGroupFields());
            QueryVO row = resultMap.computeIfAbsent(rowKey, key -> new QueryVO()
                    .setGroupKey(key.buildGroupKey(safeParams.getGroupFields()))
                    .setDeptCode(key.deptCode)
                    .setLeaderId(key.leaderId)
                    .setProjectId(key.projectId)
                    .setProjectCurrentStage(key.projectCurrentStage)
                    .setBudgetCategoryId(key.budgetCategoryId)
                    .setTimeData(initTimeData(timeKeys)));
            String bucketKey = resolveTimeKey(safeParams.getTimeGranularity(), fact.getStatMonth());
            if (bucketKey == null || !row.getTimeData().containsKey(bucketKey)) {
                continue;
            }
            row.getTimeData().put(bucketKey, row.getTimeData().get(bucketKey).add(defaultZero(fact.getAmountTax())));
        }
        return resultMap.entrySet().stream()
                .sorted((left, right) -> compareRowKey(left.getKey(), right.getKey(), safeParams.getGroupFields()))
                .map(Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public DrilldownVO drilldownOa(DrilldownParams params) throws BusinessException {
        Date start = resolveBucketStart(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        Date end = resolveBucketEnd(params.getTimeGranularity(), params.getRangeStart(), params.getTimeUnitKey());
        return new DrilldownVO()
                .setDeptCode(params.getDeptCode())
                .setDeptCodes(resolveRange(params.getDeptCode(), params.getDeptCodes()))
                .setLeaderId(params.getLeaderId())
                .setLeaderIds(resolveRange(params.getLeaderId(), params.getLeaderIds()))
                .setProjectId(params.getProjectId())
                .setProjectIds(resolveRange(params.getProjectId(), params.getProjectIds()))
                .setBudgetCategoryIds(params.getBudgetCategoryId() == null ? Collections.emptyList() : Collections.singletonList(params.getBudgetCategoryId()))
                .setAccountDateStart(start)
                .setAccountDateEnd(end)
                .setApprovalStates(Arrays.asList("Approving", "Agreed"));
    }

    private <T> List<T> resolveRange(T rowValue, List<T> filterValues) {
        if (rowValue != null) {
            return Collections.singletonList(rowValue);
        }
        return CollectionUtils.isEmpty(filterValues) ? Collections.emptyList() : new ArrayList<>(filterValues);
    }

    private QueryParams normalize(QueryParams params) {
        QueryParams queryParams = params == null ? new QueryParams() : params;
        if (queryParams.getTimeGranularity() == null) {
            queryParams.setTimeGranularity(TimeGranularity.MONTH);
        }
        if (queryParams.getRangeStart() == null) {
            Date nowMonth = DateUtils.truncate(new Date(), Calendar.MONTH);
            queryParams.setRangeStart(nowMonth);
            queryParams.setRangeEnd(nowMonth);
        }
        if (queryParams.getRangeEnd() == null) {
            queryParams.setRangeEnd(queryParams.getRangeStart());
        }
        if (queryParams.getGroupFields() == null) {
            queryParams.setGroupFields(Collections.emptyList());
        }
        return queryParams;
    }

    private LambdaQueryWrapper<AnalysisFactActual> buildQuery(QueryParams params) {
        LambdaQueryWrapper<AnalysisFactActual> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AnalysisFactActual::getSourceType, AnalysisSourceType.OA_ACTUAL);
        queryWrapper.eq(AnalysisFactActual::getDeleted, 0);
        queryWrapper.isNotNull(AnalysisFactActual::getProjectId);
        queryWrapper.ge(AnalysisFactActual::getStatMonth, DateUtils.truncate(params.getRangeStart(), Calendar.MONTH));
        queryWrapper.le(AnalysisFactActual::getStatMonth, DateUtils.truncate(params.getRangeEnd(), Calendar.MONTH));
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getDeptCodes()), AnalysisFactActual::getDeptCode, params.getDeptCodes());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getLeaderIds()), AnalysisFactActual::getLeaderId, params.getLeaderIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectIds()), AnalysisFactActual::getProjectId, params.getProjectIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBudgetCategoryIds()), AnalysisFactActual::getBudgetCategoryId, params.getBudgetCategoryIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getAuthLeaderIds()), AnalysisFactActual::getLeaderId, params.getAuthLeaderIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectStages()), AnalysisFactActual::getProjectCurrentStage, params.getProjectStages());
        return queryWrapper;
    }

    private Map<String, BigDecimal> initTimeData(List<String> timeKeys) {
        Map<String, BigDecimal> data = new LinkedHashMap<>();
        timeKeys.forEach(key -> data.put(key, BigDecimal.ZERO));
        return data;
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
                return keys;
            case HALF_YEAR:
                keys.add("H1");
                keys.add("H2");
                return keys;
            case QUARTER:
                keys.add("Q1");
                keys.add("Q2");
                keys.add("Q3");
                keys.add("Q4");
                return keys;
            case MONTH:
            default:
                while (!start.after(end)) {
                    keys.add("M" + (start.get(Calendar.MONTH) + 1));
                    start.add(Calendar.MONTH, 1);
                }
                return keys;
        }
    }

    private String resolveTimeKey(TimeGranularity granularity, Date statMonth) {
        if (statMonth == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(statMonth);
        int month = calendar.get(Calendar.MONTH) + 1;
        switch (granularity) {
            case YEAR:
                return "Y1";
            case HALF_YEAR:
                return month <= 6 ? "H1" : "H2";
            case QUARTER:
                return "Q" + ((month - 1) / 3 + 1);
            case MONTH:
            default:
                return "M" + month;
        }
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
                return calendar.getTime();
            case HALF_YEAR:
                calendar.set(Calendar.MONTH, "H2".equals(timeUnitKey) ? 6 : 0);
                return calendar.getTime();
            case QUARTER:
                calendar.set(Calendar.MONTH, (parseSuffix(timeUnitKey) - 1) * 3);
                return calendar.getTime();
            case MONTH:
            default:
                calendar.set(Calendar.MONTH, parseSuffix(timeUnitKey) - 1);
                return calendar.getTime();
        }
    }

    private Date resolveBucketEnd(TimeGranularity granularity, Date rangeStart, String timeUnitKey) {
        Date start = resolveBucketStart(granularity, rangeStart, timeUnitKey);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
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
        calendar.add(Calendar.MILLISECOND, -1);
        return calendar.getTime();
    }

    private int parseSuffix(String timeUnitKey) {
        return Integer.parseInt(timeUnitKey.substring(1));
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int compareRowKey(RowKey left, RowKey right, List<String> groupFields) {
        for (String groupField : groupFields) {
            int result;
            switch (groupField) {
                case "dept":
                    result = compareNullable(left.deptCode, right.deptCode);
                    break;
                case "leader":
                    result = compareNullable(left.leaderId, right.leaderId);
                    break;
                case "project":
                    result = compareNullable(left.projectId, right.projectId);
                    break;
                case "stage":
                    result = compareNullable(left.projectCurrentStage, right.projectCurrentStage);
                    break;
                default:
                    result = 0;
                    break;
            }
            if (result != 0) {
                return result;
            }
        }
        return compareNullable(left.budgetCategoryId, right.budgetCategoryId);
    }

    private <T extends Comparable<? super T>> int compareNullable(T left, T right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private static class RowKey {
        private String deptCode;
        private Long leaderId;
        private Long projectId;
        private String projectCurrentStage;
        private Long budgetCategoryId;

        static RowKey fromFact(AnalysisFactActual fact, List<String> groupFields) {
            RowKey rowKey = new RowKey();
            rowKey.budgetCategoryId = fact.getBudgetCategoryId();
            rowKey.deptCode = groupFields.contains("dept") ? fact.getDeptCode() : null;
            rowKey.leaderId = groupFields.contains("leader") ? fact.getLeaderId() : null;
            rowKey.projectId = groupFields.contains("project") ? fact.getProjectId() : null;
            rowKey.projectCurrentStage = groupFields.contains("stage") ? fact.getProjectCurrentStage() : null;
            return rowKey;
        }

        String buildGroupKey(List<String> groupFields) {
            StringBuilder result = new StringBuilder();
            for (String groupField : groupFields) {
                switch (groupField) {
                    case "dept":
                        appendKeyPart(result, groupField, deptCode);
                        break;
                    case "leader":
                        appendKeyPart(result, groupField, leaderId);
                        break;
                    case "project":
                        appendKeyPart(result, groupField, projectId);
                        break;
                    case "stage":
                        appendKeyPart(result, groupField, projectCurrentStage);
                        break;
                    default:
                        break;
                }
            }
            return result.toString();
        }

        private static void appendKeyPart(StringBuilder result, String field, Object value) {
            String text = value == null ? null : String.valueOf(value);
            result.append(field).append(':');
            if (text == null) {
                result.append("-1:");
            } else {
                result.append(text.length()).append(':').append(text);
            }
            result.append('|');
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RowKey rowKey = (RowKey) o;
            return Objects.equals(deptCode, rowKey.deptCode)
                    && Objects.equals(leaderId, rowKey.leaderId)
                    && Objects.equals(projectId, rowKey.projectId)
                    && Objects.equals(projectCurrentStage, rowKey.projectCurrentStage)
                    && Objects.equals(budgetCategoryId, rowKey.budgetCategoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deptCode, leaderId, projectId, projectCurrentStage, budgetCategoryId);
        }
    }
}
