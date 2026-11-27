package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.SecurityDepositBalance;
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
 * @author makejava
 * @since 2025-07-25 16:47:08
 */
@Validated
public interface SecurityDepositBalanceService {

    @ApiOperation(value = "查询")
    PageResponse<SecurityDepositBalance> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "分组查询")
    PageResponse<SecurityDepositBalance> groupQuery(QueryParams params) throws BusinessException;

    @ApiOperation(value = "统计")
    SecurityDepositBalance sum(QueryParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "SecurityDepositBalanceService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("往来单位性质")
        private String partnerType;
        @ApiModelProperty("供应商/客户ID")
        private Long scId;
        @ApiModelProperty("供应商/客户ID")
        private List<Long> scIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID")
        private List<Long> businessBodyIds;
        @ApiModelProperty("分组字段")
        private List<String> groups;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "SecurityDepositBalanceService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("往来单位性质")
        private String partnerType;
        @ApiModelProperty("供应商/客户ID")
        private Long scId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("支付金额")
        private BigDecimal payAmount;
        @ApiModelProperty("收回本金")
        private BigDecimal receiptedAmount;
        @ApiModelProperty("未收回金额")
        private BigDecimal unreceiptedAmount;
        @ApiModelProperty("实际收回时间")
        private Date actualDate;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        SecurityDepositBalance copyProperties(AddParams params);

    }
}
