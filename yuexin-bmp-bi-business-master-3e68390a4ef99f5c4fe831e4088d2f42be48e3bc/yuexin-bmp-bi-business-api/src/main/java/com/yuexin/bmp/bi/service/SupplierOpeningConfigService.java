package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.SupplierOpeningConfig;
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
 * @since 2025-01-08 16:06:24
 */
@Validated
public interface SupplierOpeningConfigService {

    @ApiOperation(value = "查询")
    PageResponse<SupplierOpeningConfig> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "SupplierOpeningConfigService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("供应商ID")
        private List<Long> supplierIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID")
        private List<Long> businessBodyIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "SupplierOpeningConfigService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("期初日期")
        private Date openingDate;
        @ApiModelProperty("未结算金额")
        private BigDecimal unsettledAmount;
        @ApiModelProperty("已结算未支付金额")
        private BigDecimal settledUnpaidAmount;
        @ApiModelProperty("已结算已支付金额")
        private BigDecimal settledPaidAmount;
        @ApiModelProperty("采购退款金额")
        private BigDecimal purchaseRefundAmount;
        @ApiModelProperty("采购退票金额")
        private BigDecimal purchaseReturnAmount;
        @ApiModelProperty("收票中金额")
        private BigDecimal invoicingAmount;
        @ApiModelProperty("已收票金额")
        private BigDecimal invoicedAmount;
        @ApiModelProperty("操作人")
        private Long operatorId;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        SupplierOpeningConfig copyProperties(AddParams params);
    }
}
