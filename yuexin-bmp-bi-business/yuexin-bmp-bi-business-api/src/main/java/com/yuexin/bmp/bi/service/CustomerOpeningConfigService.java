package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.CustomerOpeningConfig;
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
 * @since 2025-01-08 16:07:23
 */
@Validated
public interface CustomerOpeningConfigService {

    @ApiOperation(value = "查询")
    PageResponse<CustomerOpeningConfig> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "CustomerOpeningConfigService.QueryParams")
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
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "CustomerOpeningConfigService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("期初日期")
        private Date openingDate;
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
        @ApiModelProperty("操作人")
        private Long operatorId;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        CustomerOpeningConfig copyProperties(AddParams params);
    }
}
