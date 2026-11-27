package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.CustomerTradingReport;
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
public interface CustomerTradingReportService {

    PageResponse<CustomerTradingReport> query(QueryParams params) throws BusinessException;

    CustomerTradingReport sum(QueryParams params) throws BusinessException;

    void batchAdd(List<AddParams> params) throws BusinessException;

    void batchDelete(DeleteParams params) throws BusinessException;

    @Data
    @Accessors(chain = true)
    class DeleteParams implements Serializable {
        @ApiModelProperty("月份")
        private Date month;
    }

    @Data
    @Accessors(chain = true)
    class AddParams implements Serializable {
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("已回款金额")
        private BigDecimal paidAmount;
        @ApiModelProperty("未回款金额(已结算)")
        private BigDecimal unpaidAmount;
        @ApiModelProperty("已开票金额")
        private BigDecimal ticketAmount;
        @ApiModelProperty("未开票金额(已结算)")
        private BigDecimal nonTicketAmount;
        @ApiModelProperty(value = "未开票金额(总)")
        private BigDecimal nonTicketAmountSum;
        @ApiModelProperty("应收金额(已结算)")
        private BigDecimal incomeAmount;
        @ApiModelProperty("应收金额(未结算)")
        private BigDecimal incomeAmountNot;
    }


    @Data
    @Accessors(chain = true)
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("客户ID列表")
        private List<Long> customerIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID列表")
        private List<Long> businessBodyIds;
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
        @ApiModelProperty("客户ID列表")
        private List<Long> customerIdList;
        @ApiModelProperty("未开票金额是否大于0")
        private Boolean nonTicketAmount;
        @ApiModelProperty("未回款金额是否大于0")
        private Boolean unpaidAmount;
    }


    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        CustomerTradingReport copyProperties(AddParams params);

    }
}
