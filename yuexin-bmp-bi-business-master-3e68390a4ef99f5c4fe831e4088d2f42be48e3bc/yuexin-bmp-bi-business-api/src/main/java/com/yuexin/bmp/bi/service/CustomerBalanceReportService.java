package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.CustomerBalanceReport;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author jiachun
 * @date 2024/1/23
 */
public interface CustomerBalanceReportService {

    PageResponse<CustomerBalanceReport> query(QueryParams params) throws BusinessException;

    PageResponse<CustomerBalanceReport> groupQuery(QueryParams params) throws BusinessException;

    StatVO sum(QueryParams params) throws BusinessException;

    void batchAdd(List<AddParams> params) throws BusinessException;

    void delete() throws BusinessException;

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
        @ApiModelProperty("业务主体ID")
        private List<Long> businessBodyIds;
        @ApiModelProperty("分组字段")
        private List<String> groups;
    }

    @Data
    @Accessors(chain = true)
    @ApiModel(value = "CustomerBalanceReportController.StatVO")
    class StatVO implements Serializable {
        @ApiModelProperty("总应收金额")
        private BigDecimal totalAmount = BigDecimal.ZERO;
        @ApiModelProperty("未结算金额")
        private BigDecimal unsettledAmount = BigDecimal.ZERO;
        @ApiModelProperty("未出账未结算金额")
        private BigDecimal billUnsettledAmount = BigDecimal.ZERO;
        @ApiModelProperty("已出账未结算金额")
        private BigDecimal billedUnsettledAmount = BigDecimal.ZERO;
        @ApiModelProperty("已结算未开票金额")
        private BigDecimal settledInvoiceAmount = BigDecimal.ZERO;
        @ApiModelProperty("已结算已开票金额")
        private BigDecimal settledInvoicedAmount = BigDecimal.ZERO;
        @ApiModelProperty("销售退票金额")
        private BigDecimal salesReturnAmount = BigDecimal.ZERO;
        @ApiModelProperty("销售退款金额")
        private BigDecimal salesRefundAmount = BigDecimal.ZERO;
        @ApiModelProperty("已回款金额")
        private BigDecimal paidAmount = BigDecimal.ZERO;
        @ApiModelProperty("疑似回款金额")
        private BigDecimal suspectedPaidAmount = BigDecimal.ZERO;
        @ApiModelProperty("已结算未回款金额(包含疑似回款)")
        private BigDecimal settledUnpaidWithAmount = BigDecimal.ZERO;
        @ApiModelProperty("应收-已结算未回款金额(包含疑似回款)")
        private BigDecimal recSettledUnpaidWithAmount = BigDecimal.ZERO;
        @ApiModelProperty("预收-已结算未回款金额(包含疑似回款)")
        private BigDecimal preSettledUnpaidWithAmount = BigDecimal.ZERO;
        @ApiModelProperty("已结算未回款金额(不包含疑似回款)")
        private BigDecimal settledUnpaidWithoutAmount = BigDecimal.ZERO;
        @ApiModelProperty("应收-已结算未回款金额(不包含疑似回款)")
        private BigDecimal recSettledUnpaidWithoutAmount = BigDecimal.ZERO;
        @ApiModelProperty("预收-已结算未回款金额(不包含疑似回款)")
        private BigDecimal preSettledUnpaidWithoutAmount = BigDecimal.ZERO;
        @ApiModelProperty("累计未回款金额(包含疑似回款)")
        private BigDecimal cumulativeUnpaidWithAmount = BigDecimal.ZERO;
        @ApiModelProperty("累计未回款金额(不包含疑似回款)")
        private BigDecimal cumulativeUnpaidWithoutAmount = BigDecimal.ZERO;
    }

    @Data
    @Accessors(chain = true)
    class AddParams implements Serializable {
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("总应收金额")
        private BigDecimal totalAmount;
        @ApiModelProperty("未结算金额")
        private BigDecimal unsettledAmount;
        @ApiModelProperty("未出账未结算金额")
        private BigDecimal billUnsettledAmount;
        @ApiModelProperty("已出账未结算金额")
        private BigDecimal billedUnsettledAmount;
        @ApiModelProperty("已结算未开票金额")
        private BigDecimal settledInvoiceAmount;
        @ApiModelProperty("已结算已开票金额")
        private BigDecimal settledInvoicedAmount;
        @ApiModelProperty("销售退票金额")
        private BigDecimal salesReturnAmount;
        @ApiModelProperty("销售退款金额")
        private BigDecimal salesRefundAmount;
        @ApiModelProperty("已回款金额")
        private BigDecimal paidAmount;
        @ApiModelProperty("疑似回款金额")
        private BigDecimal suspectedPaidAmount;
        @ApiModelProperty("已结算未回款金额(包含疑似回款)")
        private BigDecimal settledUnpaidWithAmount;
        @ApiModelProperty("已结算未回款金额(不包含疑似回款)")
        private BigDecimal settledUnpaidWithoutAmount;
        @ApiModelProperty("累计未回款金额(包含疑似回款)")
        private BigDecimal cumulativeUnpaidWithAmount;
        @ApiModelProperty("累计未回款金额(不包含疑似回款)")
        private BigDecimal cumulativeUnpaidWithoutAmount;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        CustomerBalanceReport copyProperties(AddParams params);

    }
}
