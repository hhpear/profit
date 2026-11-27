package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.AnalysisFactActual;
import com.yuexin.bmp.bi.common.AnalysisSourceType;
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
public interface AnalysisFactActualService {

    @ApiOperation("查询")
    List<AnalysisFactActual> query(QueryParams params) throws BusinessException;

    @ApiOperation("批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @ApiOperation("删除")
    void delete(DeleteParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("AnalysisFactActualService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("来源类型")
        private AnalysisSourceType sourceType;
        @ApiModelProperty("来源类型")
        private List<AnalysisSourceType> sourceTypes;
        @ApiModelProperty("统计月份")
        private Date statMonth;
        @ApiModelProperty("统计月份开始")
        private Date statMonthStart;
        @ApiModelProperty("统计月份结束")
        private Date statMonthEnd;
        @ApiModelProperty("项目ID")
        private Long projectId;
        @ApiModelProperty("项目ID")
        private List<Long> projectIds;
        @ApiModelProperty("部门编码")
        private String deptCode;
        @ApiModelProperty("负责人ID")
        private Long leaderId;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("AnalysisFactActualService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("来源类型")
        private AnalysisSourceType sourceType;
        @ApiModelProperty("源记录ID")
        private Long sourceId;
        @ApiModelProperty("源单号")
        private String sourceCode;
        @ApiModelProperty("扩展子类型")
        private String sourceSubType;
        @ApiModelProperty("统计月份")
        private Date statMonth;
        @ApiModelProperty("年份")
        private Integer yearNum;
        @ApiModelProperty("二级预算分类ID")
        private Long budgetCategoryId;
        @ApiModelProperty("一级预算分类ID")
        private Long budgetParentId;
        @ApiModelProperty("归属部门编码")
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
        @ApiModelProperty("项目当前状态")
        private String projectCurrentStage;
        @ApiModelProperty("含税金额")
        private BigDecimal amountTax;
        @ApiModelProperty("未税金额")
        private BigDecimal amountUntax;
        @ApiModelProperty("审批状态")
        private String approvalState;
        @ApiModelProperty("订单状态")
        private String orderState;
        @ApiModelProperty("订单属性")
        private String orderProperty;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("大平台企业名称")
        private String entName;
        @ApiModelProperty("账号")
        private String accountNumber;
        @ApiModelProperty("计费数")
        private Long feeCount;
        @ApiModelProperty("实际花销人ID")
        private Long actualOwnerId;
        @ApiModelProperty("摘要")
        private String summary;
        @ApiModelProperty("提交人ID")
        private Long submitterId;
        @ApiModelProperty("提交时间")
        private Date applyTime;
        @ApiModelProperty("完成时间")
        private Date approveTime;
        @ApiModelProperty("入账日期")
        private Date accountDate;
        @ApiModelProperty("关联审批ID")
        private Long linkOaId;
        @ApiModelProperty("关联审批类型")
        private String linkOaType;
        @ApiModelProperty("项目所属供应商")
        private String supplierName;
        @ApiModelProperty("审批编号")
        private String approvalCode;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("通道名称")
        private String channelName;
        @ApiModelProperty("账号名称")
        private String accountName;
        @ApiModelProperty("一级代理费规则")
        private String firstAgentRule;
        @ApiModelProperty("二级代理费规则")
        private String secondAgentRule;
        @ApiModelProperty("三级代理费规则")
        private String thirdAgentRule;
        @ApiModelProperty("关联审批编号")
        private String linkOaCode;
        @ApiModelProperty("归属快照时间")
        private Date snapshotTime;
        @ApiModelProperty("业务日期")
        private Date bizDate;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel("AnalysisFactActualService.DeleteParams")
    class DeleteParams implements Serializable {
        @ApiModelProperty("来源类型")
        private AnalysisSourceType sourceType;
        @ApiModelProperty("统计月份开始")
        private Date statMonthStart;
        @ApiModelProperty("统计月份结束")
        private Date statMonthEnd;
    }

    BeanConverter BEAN_CONVERTER = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        AnalysisFactActual copyProperties(AddParams params);
    }
}
