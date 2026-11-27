package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.AnalysisFactBudget;
import com.yuexin.bmp.bi.common.BudgetBizType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Validated
public interface AnalysisFactBudgetService {

    @ApiOperation("查询")
    List<AnalysisFactBudget> query(QueryParams params) throws BusinessException;

    @ApiOperation("批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @ApiOperation("删除")
    void delete(DeleteParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("AnalysisFactBudgetService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("业务类型")
        private BudgetBizType bizType;
        @ApiModelProperty("业务类型")
        private List<BudgetBizType> bizTypes;
        @ApiModelProperty("统计月份")
        private Date statMonth;
        @ApiModelProperty("统计月份开始")
        private Date statMonthStart;
        @ApiModelProperty("统计月份结束")
        private Date statMonthEnd;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("AnalysisFactBudgetService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("业务类型")
        private BudgetBizType bizType;
        @ApiModelProperty("源预算ID")
        private Long sourceBudgetId;
        @ApiModelProperty("源审批记录ID")
        private Long sourceApprovalId;
        @ApiModelProperty("预算月份")
        private Date statMonth;
        @ApiModelProperty("年份")
        private Integer yearNum;
        @ApiModelProperty("二级预算分类ID")
        private Long budgetCategoryId;
        @ApiModelProperty("一级预算分类ID")
        private Long budgetParentId;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("收入类型ID")
        private Long incomeType;
        @ApiModelProperty("项目标签ID")
        private Long projectLabelId;
        @ApiModelProperty("项目标签")
        private String projectLabel;
        @ApiModelProperty("项目池唯一键")
        private String projectPoolKey;
        @ApiModelProperty("初始预算")
        private BigDecimal budgetAmountBegin;
        @ApiModelProperty("最终预算")
        private BigDecimal budgetAmountEnd;
        @ApiModelProperty("预算差")
        private BigDecimal budgetAmountDiff;
        @ApiModelProperty("预算状态")
        private String state;
        @ApiModelProperty("快照时间")
        private Date snapshotTime;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("AnalysisFactBudgetService.DeleteParams")
    class DeleteParams implements Serializable {
        @ApiModelProperty("业务类型")
        private BudgetBizType bizType;
        @ApiModelProperty("统计月份开始")
        private Date statMonthStart;
        @ApiModelProperty("统计月份结束")
        private Date statMonthEnd;
    }

    BeanConverter BEAN_CONVERTER = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        AnalysisFactBudget copyProperties(AddParams params);
    }
}
