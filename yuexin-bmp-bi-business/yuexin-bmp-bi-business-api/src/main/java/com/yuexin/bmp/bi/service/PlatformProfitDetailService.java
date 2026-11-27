package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.BusinessEnum;
import com.yuexin.bmp.bi.common.Cycle;
import com.yuexin.bmp.bi.common.PlatformProfitDetail;
import com.yuexin.bmp.bi.common.ProfitEnum;
import com.yuexin.bmp.bi.common.StatSource;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
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
 * 平台利润明细(PlatformProfitDetail)表服务接口
 *
 * @author lxf
 * @since 2024-01-31 18:48:33
 */
@Validated
public interface PlatformProfitDetailService {

    @ApiOperation(value = "查询")
    PageResponse<PlatformProfitDetail> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询短信收入日明细")
    PageResponse<PlatformProfitDetail> querySmsDay(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询短信收入月明细")
    PageResponse<PlatformProfitDetail> querySmsMonth(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询返佣日明细")
    PageResponse<PlatformProfitDetail> queryRebateDay(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询返佣月明细")
    PageResponse<PlatformProfitDetail> queryRebateMonth(QueryParams params) throws BusinessException;

    @ApiOperation(value = "统计")
    PlatformProfitDetail sum(QueryParams params) throws BusinessException;

    @ApiOperation(value = "分组查询")
    PageResponse<PlatformProfitDetail> groupQuery(QueryParams params) throws BusinessException;

    @ApiOperation(value = "统计")
    List<PlatformProfitDetail> stat(QueryParams params) throws BusinessException;

    @ApiOperation(value = "新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    void batchUpdate(List<PlatformProfitDetail> params) throws BusinessException;

    void batchDelete(DeleteParams params) throws BusinessException;

    void updateIncr(@Valid IncrStockParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PlatformProfitDetailService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("客户ID")
        private List<Long> customerIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID")
        private List<Long> businessBodyIds;
        @ApiModelProperty("销售项目ID")
        private Long projectId;
        @ApiModelProperty("销售项目ID列表")
        private List<Long> projectIds;
        @ApiModelProperty("项目负责人ID")
        private Long projectLeaderId;
        @ApiModelProperty("项目负责人ID")
        private List<Long> projectLeaderIds;
        @ApiModelProperty("销售指标")
        private Boolean marketIndex;
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("日期")
        private Date time;
        @ApiModelProperty("统计来源")
        private StatSource source;
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("企业编码")
        private List<String> entCodes;
        @ApiModelProperty("平台和企业编码")
        private List<String> platformEntCodes;
        @ApiModelProperty("账号")
        private String accountNumber;
        @ApiModelProperty("账号")
        private List<String> accountNumbers;
        @ApiModelProperty("负利润")
        private ProfitEnum profitEnum;
        @ApiModelProperty("业务下降")
        private BusinessEnum businessEnum;
        @ApiModelProperty("增量/存量")
        private String incrStock;
        @ApiModelProperty("分组字段")
        private List<String> groups;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PlatformProfitDetailService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("销售项目ID")
        private Long projectId;
        @ApiModelProperty("项目负责人ID")
        private Long projectLeaderId;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("账号")
        private String accountNumber;
        @ApiModelProperty("付费类型")
        private String chargeType;
        @ApiModelProperty("提交数")
        private Long submitCount;
        @ApiModelProperty("上月日均提交数")
        private Long lastSubmitCount;
        @ApiModelProperty("计费数")
        private Long feeCount;
        @ApiModelProperty("上月日均计费数")
        private Long lastFeeCount;
        @ApiModelProperty("成功数")
        private Long successCount;
        @ApiModelProperty("上月日均成功数")
        private Long lastSuccessCount;
        @ApiModelProperty("短信收入")
        private BigDecimal amount;
        @ApiModelProperty("短信收入(不含税)")
        private BigDecimal amountUntax;
        @ApiModelProperty("上月短信收入")
        private BigDecimal lastAmount;
        @ApiModelProperty("上月短信收入(不含税)")
        private BigDecimal lastAmountUntax;
        @ApiModelProperty("返佣规则")
        private Long rebateId;
        @ApiModelProperty("返佣金额")
        private BigDecimal rebateCount;
        @ApiModelProperty("返佣金额(不含税)")
        private BigDecimal rebateCountUntax;
        @ApiModelProperty("日期")
        private Date time;
        @ApiModelProperty("统计来源")
        private StatSource source;
        @ApiModelProperty("出账状态")
        private String state;
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("通道销售成本")
        private BigDecimal channelMarketCost;
        @ApiModelProperty("通道销售成本(不含税)")
        private BigDecimal channelMarketCostUntax;
        @ApiModelProperty("返佣后短信销售利润")
        private BigDecimal smsMarketProfit;
        @ApiModelProperty("返佣后短信销售利润(不含税)")
        private BigDecimal smsMarketProfitUntax;
        @ApiModelProperty("上月返佣后短信销售利润")
        private BigDecimal lastSmsMarketProfit;
        @ApiModelProperty("上月返佣后短信销售利润(不含税)")
        private BigDecimal lastSmsMarketProfitUntax;
        @ApiModelProperty("短信销售利润(包含返佣)")
        private BigDecimal smsMarketWithProfit;
        @ApiModelProperty("短信销售利润(包含返佣不含税)")
        private BigDecimal smsMarketWithProfitUntax;
        @ApiModelProperty("上月短信销售利润(包含返佣)")
        private BigDecimal lastSmsMarketWithProfit;
        @ApiModelProperty("上月短信销售利润(包含返佣不含税)")
        private BigDecimal lastSmsMarketWithProfitUntax;
        @ApiModelProperty("通道运营成本")
        private BigDecimal channelOperateCost;
        @ApiModelProperty("通道运营成本(不含税)")
        private BigDecimal channelOperateCostUntax;
        @ApiModelProperty("返佣后短信运营利润")
        private BigDecimal smsOperateProfit;
        @ApiModelProperty("返佣后短信运营利润(不含税)")
        private BigDecimal smsOperateProfitUntax;
        @ApiModelProperty("上月返佣后短信运营利润")
        private BigDecimal lastSmsOperateProfit;
        @ApiModelProperty("上月返佣后短信运营利润(不含税)")
        private BigDecimal lastSmsOperateProfitUntax;
        @ApiModelProperty("短信运营利润(包含返佣)")
        private BigDecimal smsOperateWithProfit;
        @ApiModelProperty("短信运营利润(包含返佣不含税)")
        private BigDecimal smsOperateWithProfitUntax;
        @ApiModelProperty("上月短信运营利润(包含返佣)")
        private BigDecimal lastSmsOperateWithProfit;
        @ApiModelProperty("上月短信运营利润(包含返佣不含税)")
        private BigDecimal lastSmsOperateWithProfitUntax;
        @ApiModelProperty("返佣后短信销售利润率")
        private BigDecimal smsMarketRate;
        @ApiModelProperty("上月返佣后短信销售利润率")
        private BigDecimal lastSmsMarketRate;
        @ApiModelProperty("短信销售利润率(包含返佣)")
        private BigDecimal smsMarketWithRate;
        @ApiModelProperty("上月短信销售利润率(包含返佣)")
        private BigDecimal lastSmsMarketWithRate;
        @ApiModelProperty("返佣后短信运营利润率")
        private BigDecimal smsOperateRate;
        @ApiModelProperty("上月返佣后短信运营利润率")
        private BigDecimal lastSmsOperateRate;
        @ApiModelProperty("短信运营利润率(包含返佣)")
        private BigDecimal smsOperateWithRate;
        @ApiModelProperty("上月短信运营利润率(包含返佣)")
        private BigDecimal lastSmsOperateWithRate;
        @ApiModelProperty("增量/存量")
        private String incrStock;
    }

    @Data
    @Accessors(chain = true)
    class DeleteParams implements Serializable {
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
        @ApiModelProperty("统计来源")
        private StatSource source;
    }

    @Data
    @Accessors(chain = true)
    class IncrStockParams implements Serializable {
        @ApiModelProperty("客户ID")
        @NotNull
        private Long customerId;
        @NotNull
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("开始时间")
        @NotNull
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        PlatformProfitDetail copyProperties(AddParams params);

    }
}
