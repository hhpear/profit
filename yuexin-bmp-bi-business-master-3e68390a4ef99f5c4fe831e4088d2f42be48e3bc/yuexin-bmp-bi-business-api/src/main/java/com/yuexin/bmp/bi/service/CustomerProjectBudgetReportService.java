package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.CustomerProjectBudgetReport;
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
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author lxf
 * @since 2025-09-22 14:22:57
 */
@Validated
public interface CustomerProjectBudgetReportService {

    @ApiOperation(value = "分组查询")
    PageResponse<CustomerProjectBudgetReport> groupQuery(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询")
    PageResponse<CustomerProjectBudgetReport> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "统计")
    List<CustomerProjectBudgetReport> sum(QueryParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @ApiOperation(value = "删除")
    void delete(@Valid DelParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "CustomerProjectBudgetReportService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("部门编码")
        private List<String> deptCodes;
        @ApiModelProperty("收入类型")
        private Long incomeType;
        @ApiModelProperty("收入类型")
        private List<Long> incomeTypes;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("客户ID")
        private List<Long> customerIds;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("项目ID")
        private List<Long> projectIds;
        @ApiModelProperty("项目标签ID")
        private List<Long> projectLabelIds;
        @ApiModelProperty("年度")
        private Integer year;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("月份-开始")
        private Date monthStart;
        @ApiModelProperty("月份-结束")
        private Date monthEnd;
        @ApiModelProperty("分组字段")
        private List<String> groups;
        @ApiModelProperty("权限控制")
        private List<Long> authIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "CustomerProjectBudgetReportService.GetParams")
    class GetParams implements Serializable {
        @ApiModelProperty("主键")
        @NotNull
        private Long id;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "CustomerProjectBudgetReportService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("收入类型")
        private Long incomeType;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
        @ApiModelProperty("项目标签ID")
        private Long projectLabelId;
        @ApiModelProperty("项目标签")
        private String projectLabel;
        @ApiModelProperty("年度")
        private Integer year;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("预算分类")
        private Long budgetCategoryId;
        @ApiModelProperty("初始预算")
        private BigDecimal budgetAmountBegin;
        @ApiModelProperty("最终预算")
        private BigDecimal budgetAmountEnd;
        @ApiModelProperty("预算差")
        private BigDecimal budgetAmountDiff;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "CustomerProjectBudgetReportService.DelParams")
    class DelParams implements Serializable {
        @ApiModelProperty("年度")
        private Integer year;
        @ApiModelProperty("部门编码")
        private String deptCode;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        CustomerProjectBudgetReport copyProperties(AddParams params);

    }
}
