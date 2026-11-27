package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.DeptBudgetReport;
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

/**
 * @author lxf
 * @since 2025-09-22 14:23:46
 */
@Validated
public interface DeptBudgetReportService {

    @ApiOperation(value = "查询")
    List<DeptBudgetReport> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @ApiOperation(value = "删除")
    void delete(@Valid DelParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "DeptBudgetReportService.QueryParams")
    class QueryParams implements Serializable {
        //@ApiModelProperty("分页参数")
        //private PageParams page;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("部门编码")
        private List<String> deptCodes;
        @ApiModelProperty("预算分类")
        private Long budgetCategoryId;
        @ApiModelProperty("预算分类")
        private List<Long> budgetCategoryIds;
        @ApiModelProperty("年度")
        private Integer year;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("月份-开始")
        private Date monthStart;
        @ApiModelProperty("月份-结束")
        private Date monthEnd;
        @ApiModelProperty("权限控制")
        private List<String> authDeptCodes;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "DeptBudgetReportService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("预算分类")
        private Long budgetCategoryId;
        @ApiModelProperty("年度")
        private Integer year;
        @ApiModelProperty("月份")
        private Date month;
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
    @ApiModel(value = "DeptBudgetReportService.DelParams")
    class DelParams implements Serializable {
        @ApiModelProperty("年度")
        private Integer year;
        @ApiModelProperty("部门编码")
        private String deptCode;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        DeptBudgetReport copyProperties(AddParams params);

    }
}
