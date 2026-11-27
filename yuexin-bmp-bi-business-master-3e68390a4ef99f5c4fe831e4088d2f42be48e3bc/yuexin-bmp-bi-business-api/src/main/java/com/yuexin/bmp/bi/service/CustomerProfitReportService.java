package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.CustomerProfitReport;
import com.yuexin.bmp.bi.common.Cycle;
import com.yuexin.bmp.bi.common.StatSource;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author jiachun
 * @date 2024/1/16
 */
@Validated
public interface CustomerProfitReportService {

    PageResponse<CustomerProfitReport> query(QueryParams params) throws BusinessException;

    CustomerProfitReport sum(QueryParams params) throws BusinessException;

    List<CustomerProfitReport> sumGroupByCustomer(QueryParams params) throws BusinessException;

    void batchAdd(List<AddParams> params);

    void batchDelete(DeleteParams params) throws BusinessException;

    List<CustomerProfitReport> query() throws BusinessException;

    void updateIncr(@Valid IncrStockParams params) throws BusinessException;

    void updateLeader(@Valid LeaderParams params) throws BusinessException;

    @Data
    @Accessors(chain = true)
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
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("时间")
        private Date time;
        @ApiModelProperty("统计来源")
        private StatSource source;
        @ApiModelProperty("短信预收款")
        private BigDecimal smsPrepayment;
        @ApiModelProperty("短信预收款(不含税)")
        private BigDecimal smsPrepaymentUntax;
        @ApiModelProperty("短信收入")
        private BigDecimal smsIncome;
        @ApiModelProperty("短信收入(不含税)")
        private BigDecimal smsIncomeUntax;
        @ApiModelProperty("项目收入")
        private BigDecimal projectIncome;
        @ApiModelProperty("项目收入(不含税)")
        private BigDecimal projectIncomeUntax;
        @ApiModelProperty("返佣")
        private BigDecimal rebate;
        @ApiModelProperty("返佣(不含税)")
        private BigDecimal rebateUntax;
        @ApiModelProperty("通道销售成本")
        private BigDecimal channelMarketCost;
        @ApiModelProperty("通道销售成本(不含税)")
        private BigDecimal channelMarketCostUntax;
        @ApiModelProperty("通道运营成本")
        private BigDecimal channelOperateCost;
        @ApiModelProperty("通道运营成本(不含税)")
        private BigDecimal channelOperateCostUntax;
        @ApiModelProperty("项目费用")
        private BigDecimal projectCost;
        @ApiModelProperty("短信销售利润")
        private BigDecimal smsMarketProfit;
        @ApiModelProperty("短信销售利润(不含税)")
        private BigDecimal smsMarketProfitUntax;
        @ApiModelProperty("短信运营利润")
        private BigDecimal smsOperateProfit;
        @ApiModelProperty("短信运营利润(不含税)")
        private BigDecimal smsOperateProfitUntax;
        @ApiModelProperty("项目利润")
        private BigDecimal projectProfit;
        @ApiModelProperty("项目利润(不含税)")
        private BigDecimal projectProfitUntax;
        @ApiModelProperty("计费数")
        private Long feeCount;
        @ApiModelProperty("成功数")
        private Long successCount;
        @ApiModelProperty("增量/存量")
        private String incrStock;
    }

    @Data
    @Accessors(chain = true)
    class QueryParams implements Serializable {
        @ApiModelProperty("分页")
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
        private List<Long> projectLeaderIds;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("企业编码列表")
        private List<String> entCodes;
        @ApiModelProperty("平台和企业编码")
        private List<String> platformEntCodes;
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
        @ApiModelProperty("统计来源")
        private StatSource source;
        @ApiModelProperty("销售指标")
        private Boolean marketIndex;
        @ApiModelProperty("是否是正利润")
        private Boolean profit;
        @ApiModelProperty("利润排序是否正序")
        private Boolean profitIsAsc;
        @ApiModelProperty("过滤掉测试客户ID")
        private List<Long> notTestCustomerIds;
        @ApiModelProperty("增量/存量")
        private String incrStock;
        @ApiModelProperty("分组字段")
        private List<String> groups;
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

    @Data
    @Accessors(chain = true)
    class LeaderParams implements Serializable {
        @ApiModelProperty("项目ID")
        @NotEmpty
        private List<Long> projectIds;
        @NotNull
        @ApiModelProperty("项目负责人ID")
        private Long leaderId;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        CustomerProfitReport copyProperties(AddParams params);
    }
}
