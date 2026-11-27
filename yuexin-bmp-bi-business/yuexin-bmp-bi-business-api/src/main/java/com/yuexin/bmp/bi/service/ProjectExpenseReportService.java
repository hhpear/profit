package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.TimeGranularity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Validated
public interface ProjectExpenseReportService {

    @ApiOperation("查询")
    List<QueryVO> query(QueryParams params) throws BusinessException;

    @ApiOperation("OA钻取")
    DrilldownVO drilldownOa(DrilldownParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("ProjectExpenseReportService.QueryParams")
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
        @ApiModelProperty("负责人ID")
        private List<Long> leaderIds;
        @ApiModelProperty("项目ID")
        private List<Long> projectIds;
        @ApiModelProperty("项目当前状态")
        private List<String> projectStages;
        @ApiModelProperty("预算分类ID")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("分组字段")
        private List<String> groupFields;
        @ApiModelProperty("权限负责人ID")
        private List<Long> authLeaderIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("ProjectExpenseReportService.QueryVO")
    class QueryVO implements Serializable {
        @ApiModelProperty("前端合并展示使用的稳定分组Key")
        private String groupKey;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("项目当前状态")
        private String projectCurrentStage;
        @ApiModelProperty("预算分类ID")
        private Long budgetCategoryId;
        @ApiModelProperty("时间桶金额")
        private Map<String, BigDecimal> timeData;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("ProjectExpenseReportService.DrilldownParams")
    class DrilldownParams implements Serializable {
        @ApiModelProperty("时间桶Key")
        private String timeUnitKey;
        @ApiModelProperty("时间粒度")
        private TimeGranularity timeGranularity;
        @ApiModelProperty("范围开始")
        private Date rangeStart;
        @ApiModelProperty("范围结束")
        private Date rangeEnd;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("部门编码范围")
        private List<String> deptCodes;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
        @ApiModelProperty("负责人ID范围")
        private List<Long> leaderIds;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("项目ID范围")
        private List<Long> projectIds;
        @ApiModelProperty("预算分类ID")
        private Long budgetCategoryId;
        @ApiModelProperty("项目当前状态")
        private String projectCurrentStage;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("ProjectExpenseReportService.DrilldownVO")
    class DrilldownVO implements Serializable {
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("部门编码范围")
        private List<String> deptCodes;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
        @ApiModelProperty("负责人ID范围")
        private List<Long> leaderIds;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("项目ID范围")
        private List<Long> projectIds;
        @ApiModelProperty("预算分类ID")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("入账日期开始")
        private Date accountDateStart;
        @ApiModelProperty("入账日期结束")
        private Date accountDateEnd;
        @ApiModelProperty("审批状态")
        private List<String> approvalStates;
    }
}
