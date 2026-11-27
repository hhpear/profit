package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.RebateReport;
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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 返佣跟踪报表(RebateReport)表服务接口
 *
 * @author lxf
 * @since 2024-09-04 14:55:41
 */
@Validated
public interface RebateReportService {

    @ApiOperation(value = "查询")
    PageResponse<RebateReport> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询-代理商分组")
    PageResponse<RebateReport> queryAgent(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询-企业分组")
    PageResponse<RebateReport> queryEnt(QueryParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("代理商ID列表")
        private List<Long> agentIds;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("企业编码")
        private List<String> entCodes;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("月份-开始")
        private Date monthStart;
        @ApiModelProperty("月份-结束")
        private Date monthEnd;
        @ApiModelProperty("付费类型")
        private String chargeType;
        @ApiModelProperty("是否到账")
        private Boolean isReceive;
        @ApiModelProperty("是否返佣")
        private Boolean isRebate;
        @ApiModelProperty("审批流水号")
        private String serialNumber;
        @ApiModelProperty("飞书审批编号")
        private String instanceCode;
        @ApiModelProperty("状态")
        private String state;
    }

    @ApiOperation(value = "查看")
    RebateReport get(@Valid GetParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.GetParams")
    class GetParams implements Serializable {
        @ApiModelProperty("主键")
        private Long id;
        @ApiModelProperty("飞书审批编号")
        private String instanceCode;
    }

    @ApiOperation(value = "查询多个")
    List<RebateReport> multiGet(@Valid MultiGetParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.MultiGetParams")
    class MultiGetParams implements Serializable {
        @ApiModelProperty("对账单ID")
        private List<Long> statementIds;
        @ApiModelProperty("销售订单ID")
        private List<Long> settlementIds;
    }

    @ApiOperation(value = "新增")
    void add(@Valid AddParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("账号")
        private String accountNumber;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("代理商级别")
        private Integer agentLevel;
        @ApiModelProperty("代理商类型")
        private String agentType;
        @ApiModelProperty("代理商ID")
        private Long agentId;
        @ApiModelProperty("返佣规则ID")
        private Long rebateId;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("付费类型")
        private String chargeType;
        @ApiModelProperty("短信单价")
        private BigDecimal smsPrice;
        @ApiModelProperty("短信条数")
        private Long smsNum;
        @ApiModelProperty("收入金额")
        private BigDecimal incomeMoney;
        @ApiModelProperty("利润")
        private BigDecimal profit;
        @ApiModelProperty("代理金额")
        private BigDecimal agencyMoney;
        @ApiModelProperty("创建人ID")
        private Long creatorId;
        @ApiModelProperty("销售对账ID")
        private Long statementId;
        @ApiModelProperty("销售订单ID")
        private Long settlementId;
        @ApiModelProperty("类型")
        private RebateReport.Type type;
    }

    @ApiOperation(value = "修改")
    void modify(@Valid ModifyParams params) throws BusinessException;

    @ApiOperation(value = "调整实际支付代理金额")
    void adjustActualMoney(@Valid AdjustActualMoneyParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.ModifyParams")
    class ModifyParams implements Serializable {
        @ApiModelProperty("ID")
        private Long id;
        @ApiModelProperty("销售对账ID")
        private Long statementId;
        @ApiModelProperty("销售对账ID列表")
        private List<Long> statementIds;
        @ApiModelProperty("代理商ID")
        private Long agentId;
        @ApiModelProperty("销售订单ID")
        private Long settlementId;
        @ApiModelProperty("销售订单ID列表")
        private List<Long> settlementIds;
        @ApiModelProperty("到账日期")
        private Date receiveTime;
        @ApiModelProperty("备注")
        private String remark;
        @ApiModelProperty("实际支付代理金额")
        private BigDecimal actualMoney;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.AdjustActualMoneyParams")
    class AdjustActualMoneyParams implements Serializable {
        @ApiModelProperty("销售对账ID")
        private Long statementId;
        @ApiModelProperty("销售订单ID")
        private Long settlementId;
        @ApiModelProperty("代理商ID")
        @NotNull
        private Long agentId;
        @ApiModelProperty("实际支付代理金额")
        @NotNull
        private BigDecimal actualMoney;
        @ApiModelProperty("调整原因")
        @NotBlank
        private String adjustmentReason;
    }

    @ApiOperation(value = "修改代理商")
    void modifyAgent(@Valid ModifyAgentParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.ModifyParams")
    class ModifyAgentParams implements Serializable {
        @ApiModelProperty("代理商级别")
        private Integer agentLevel;
        @ApiModelProperty("代理商类型")
        private String agentType;
        @ApiModelProperty("代理商ID")
        private Long agentId;
        @ApiModelProperty("返佣规则ID")
        private Long rebateId;
    }

    @ApiOperation(value = "申请")
    void apply(@Valid ApplyParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.ApplyParams")
    class ApplyParams implements Serializable {
        @ApiModelProperty("主键")
        private List<Long> ids;
        @ApiModelProperty("代理商ID")
        private Long agentId;
        @ApiModelProperty("审批类型")
        private RebateReport.ApplyType applyType;
        @ApiModelProperty("飞书审批唯一标识")
        private String instanceCode;
        @ApiModelProperty("审批流水号")
        private String serialNumber;
        @ApiModelProperty("提交人")
        private Long submitUserId;
    }


    @ApiOperation(value = "审批")
    void approve(@Valid ApproveParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "RebateReportService.ApproveParams")
    class ApproveParams implements Serializable {
        @ApiModelProperty("飞书审批编号")
        private String instanceCode;
        @ApiModelProperty("状态")
        private RebateReport.State state;
    }


    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        RebateReport copyProperties(AddParams params);

        RebateReport copyProperties(ApplyParams params);

        RebateReport copyProperties(ModifyParams params);
    }
}
