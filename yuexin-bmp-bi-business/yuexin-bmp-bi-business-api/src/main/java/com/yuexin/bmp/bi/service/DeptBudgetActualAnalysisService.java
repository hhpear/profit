package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.AmountType;
import com.yuexin.bmp.bi.common.AnalysisRowType;
import com.yuexin.bmp.bi.common.TimeGranularity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface DeptBudgetActualAnalysisService {

    @ApiOperation("查询")
    List<QueryVO> query(QueryParams params) throws BusinessException;

    @ApiOperation("预算钻取")
    BudgetDrilldownVO drilldownBudget(BudgetDrilldownParams params) throws BusinessException;

    @ApiOperation("实际钻取")
    ActualDrilldownVO drilldownActual(ActualDrilldownParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("年份")
        private Integer year;
        @ApiModelProperty("时间粒度")
        private TimeGranularity timeGranularity;
        @ApiModelProperty("范围开始")
        private Date rangeStart;
        @ApiModelProperty("范围结束")
        private Date rangeEnd;
        @ApiModelProperty("部门编码")
        private List<String> deptCodes;
        @ApiModelProperty("预算分类ID")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("金额口径")
        private AmountType amountType;
        @ApiModelProperty("指标列")
        private List<String> metricFields;
        @ApiModelProperty("展示模式")
        private String displayMode;
        @ApiModelProperty("预算权限部门")
        private List<String> authDeptCodes;
        @ApiModelProperty("实际权限负责人")
        private List<Long> authLeaderIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.QueryVO")
    class QueryVO implements Serializable {
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("行类型")
        private AnalysisRowType rowType;
        @ApiModelProperty("行Key")
        private String rowKey;
        @ApiModelProperty("行名称")
        private String rowName;
        @ApiModelProperty("一级预算分类ID")
        private Long parentBudgetCategoryId;
        @ApiModelProperty("二级预算分类ID")
        private Long budgetCategoryId;
        @ApiModelProperty("是否展开")
        private Boolean expanded;
        @ApiModelProperty("单元格")
        private Map<String, MetricCellVO> cells;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.MetricCellVO")
    class MetricCellVO implements Serializable {
        @ApiModelProperty("初始预算")
        private BigDecimal beginBudget;
        @ApiModelProperty("最终预算")
        private BigDecimal endBudget;
        @ApiModelProperty("实际发生")
        private BigDecimal actualAmount;
        @ApiModelProperty("最终差异")
        private BigDecimal diffAmount;
        @ApiModelProperty("达成率")
        private BigDecimal reachRate;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.BudgetDrilldownParams")
    class BudgetDrilldownParams implements Serializable {
        @ApiModelProperty("时间桶")
        private String timeUnitKey;
        @ApiModelProperty("预算指标")
        private String metricField;
        @ApiModelProperty("行Key")
        private String rowKey;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("预算分类范围")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("时间粒度")
        private TimeGranularity timeGranularity;
        @ApiModelProperty("范围开始")
        private Date rangeStart;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.BudgetDrilldownVO")
    class BudgetDrilldownVO implements Serializable {
        @ApiModelProperty("部门")
        private List<String> deptCodes;
        @ApiModelProperty("预算分类")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("开始月份")
        private Date monthStart;
        @ApiModelProperty("结束月份")
        private Date monthEnd;
        @ApiModelProperty("审批状态")
        private List<String> approvalStates;
        @ApiModelProperty("录入类型")
        private List<String> inputTypes;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.ActualDrilldownParams")
    class ActualDrilldownParams implements Serializable {
        @ApiModelProperty("时间桶")
        private String timeUnitKey;
        @ApiModelProperty("行Key")
        private String rowKey;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("预算分类范围")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("金额口径")
        private AmountType amountType;
        @ApiModelProperty("时间粒度")
        private TimeGranularity timeGranularity;
        @ApiModelProperty("范围开始")
        private Date rangeStart;
        @ApiModelProperty("权限负责人")
        private List<Long> authLeaderIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.ActualDrilldownVO")
    class ActualDrilldownVO implements Serializable {
        @ApiModelProperty("Tab")
        private List<DrilldownTabVO> tabs;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.DrilldownTabVO")
    class DrilldownTabVO implements Serializable {
        @ApiModelProperty("来源类型")
        private String sourceType;
        @ApiModelProperty("来源名称")
        private String sourceName;
        @ApiModelProperty("列")
        private List<ColumnVO> columns;
        @ApiModelProperty("行数据")
        private List<Map<String, Object>> rows;
        @ApiModelProperty("合计金额")
        private BigDecimal summaryAmount;
        @ApiModelProperty("明细数量")
        private Long summaryCount;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("DeptBudgetActualAnalysisService.ColumnVO")
    class ColumnVO implements Serializable {
        @ApiModelProperty("字段")
        private String prop;
        @ApiModelProperty("标题")
        private String label;
    }
}
