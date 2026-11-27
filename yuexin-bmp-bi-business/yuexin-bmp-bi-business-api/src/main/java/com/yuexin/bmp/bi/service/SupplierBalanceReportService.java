package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.SupplierBalanceReport;
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
public interface SupplierBalanceReportService {

    PageResponse<SupplierBalanceReport> query(QueryParams params) throws BusinessException;

    PageResponse<SupplierBalanceReport> groupQuery(QueryParams params) throws BusinessException;

    SupplierBalanceReport sum(QueryParams params) throws BusinessException;

    void batchAdd(List<AddParams> params) throws BusinessException;

    void delete() throws BusinessException;

    @Data
    @Accessors(chain = true)
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("供应商ID列表")
        private List<Long> supplierIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID列表")
        private List<Long> businessBodyIds;
        @ApiModelProperty("分组字段")
        private List<String> groups;
    }

    @Data
    @Accessors(chain = true)
    class AddParams implements Serializable {
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("总应付金额")
        private BigDecimal totalAmount;
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
        @ApiModelProperty("已支付未收票金额")
        private BigDecimal paidInvoiceAmount;
        @ApiModelProperty("应付未收票金额")
        private BigDecimal payableInvoiceAmount;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        SupplierBalanceReport copyProperties(AddParams params);

    }
}
