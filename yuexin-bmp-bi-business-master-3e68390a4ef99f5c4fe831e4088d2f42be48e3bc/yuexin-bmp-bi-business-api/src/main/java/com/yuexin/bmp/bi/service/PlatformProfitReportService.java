package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.Cycle;
import com.yuexin.bmp.bi.common.PlatformProfitReport;
import com.yuexin.bmp.bi.common.StatSource;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author jiachun
 * @date 2024/1/23
 */
public interface PlatformProfitReportService {


    PageResponse<PlatformProfitReport> query(QueryParams params) throws BusinessException;

    void batchAdd(List<AddParams> params) throws BusinessException;

    void batchDelete(DeleteParams params) throws BusinessException;

    @Data
    @Accessors(chain = true)
    @ApiModel("PlatformProfitReportService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("时间")
        private Date time;
        @ApiModelProperty("统计来源")
        private StatSource source;
        @ApiModelProperty("短信销售利润")
        private BigDecimal smsMarketProfit;
        @ApiModelProperty("短信销售利润(不含税)")
        private BigDecimal smsMarketProfitUntax;
        @ApiModelProperty("短信运营利润")
        private BigDecimal smsOperateProfit;
        @ApiModelProperty("短信运营利润(不含税)")
        private BigDecimal smsOperateProfitUntax;
        @ApiModelProperty("通道销售成本")
        private BigDecimal channelMarketCost;
        @ApiModelProperty("通道销售成本(不含税)")
        private BigDecimal channelMarketCostUntax;
        @ApiModelProperty("通道运营成本")
        private BigDecimal channelOperateCost;
        @ApiModelProperty("通道运营成本(不含税)")
        private BigDecimal channelOperateCostUntax;
        @ApiModelProperty("短信收入")
        private BigDecimal smsIncome;
        @ApiModelProperty("短信收入(不含税)")
        private BigDecimal smsIncomeUntax;
        @ApiModelProperty("短信实际收入")
        private BigDecimal smsRealIncome;
        @ApiModelProperty("短信实际收入(不含税)")
        private BigDecimal smsRealIncomeUntax;
        @ApiModelProperty("项目收入")
        private BigDecimal projectIncome;
        @ApiModelProperty("项目收入(不含税)")
        private BigDecimal projectIncomeUntax;
        @ApiModelProperty("返佣")
        private BigDecimal rebate;
        @ApiModelProperty("返佣(不含税)")
        private BigDecimal rebateUntax;
        @ApiModelProperty("项目费用")
        private BigDecimal projectCost;
        @ApiModelProperty("项目利润")
        private BigDecimal projectProfit;
        @ApiModelProperty("项目利润(不含税)")
        private BigDecimal projectProfitUntax;
    }


    @Data
    @Accessors(chain = true)
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
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

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        PlatformProfitReport copyProperties(AddParams params);

    }
}
