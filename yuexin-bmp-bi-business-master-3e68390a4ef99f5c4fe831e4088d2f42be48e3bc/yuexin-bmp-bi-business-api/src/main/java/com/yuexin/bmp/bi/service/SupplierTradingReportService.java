package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.SupplierTradingReport;
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
public interface SupplierTradingReportService {

    PageResponse<SupplierTradingReport> query(QueryParams params) throws BusinessException;

    SupplierTradingReport sum(QueryParams params) throws BusinessException;

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
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("已支付金额")
        private BigDecimal paidAmount;
        @ApiModelProperty("未支付金额(已结算)")
        private BigDecimal unpaidAmount;
        @ApiModelProperty(value = "未支付金额(总)")
        private BigDecimal unpaidAmountSum;
        @ApiModelProperty("已收票金额")
        private BigDecimal ticketAmount;
        @ApiModelProperty("未收票金额(已结算)")
        private BigDecimal nonTicketAmount;
        @ApiModelProperty("应付金额(已结算)")
        private BigDecimal payableAmount;
        @ApiModelProperty("应付金额(未结算)")
        private BigDecimal payableAmountNot;
    }

    @Data
    @Accessors(chain = true)
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
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
        @ApiModelProperty("供应商ID")
        private List<Long> supplierIdList;
        @ApiModelProperty("未支付金额是否大于0")
        private Boolean unpaidAmount;
        @ApiModelProperty("未开票金额是否大于0")
        private Boolean nonTicketAmount;
    }


    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        SupplierTradingReport copyProperties(AddParams params);

    }
}
