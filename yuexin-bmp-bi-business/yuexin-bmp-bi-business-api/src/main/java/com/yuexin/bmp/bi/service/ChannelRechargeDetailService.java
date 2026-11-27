package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.ChannelRechargeDetail;
import com.yuexin.bmp.bi.common.NetType;
import com.yuexin.bmp.bi.common.SumResult;
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
 * @since 2024-11-20 13:45:08
 */
@Validated
public interface ChannelRechargeDetailService {

    @ApiOperation(value = "查询")
    PageResponse<ChannelRechargeDetail> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "时间分组查询")
    List<ChannelRechargeDetail> queryMonthGroup(QueryParams params) throws BusinessException;

    @ApiOperation(value = "时间分组查询")
    List<ChannelRechargeDetail> queryDayGroup(QueryParams params) throws BusinessException;

    @ApiOperation(value = "通道编码分组总和")
    List<ChannelRechargeDetail> querySum() throws BusinessException;

    @ApiOperation(value = "总和")
    SumResult sum(QueryParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelRechargeDetailService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("到账时间开始")
        private Date startTime;
        @ApiModelProperty("到账时间结束")
        private Date endTime;
        @ApiModelProperty("类型")
        private NetType netType;
    }

    @ApiOperation(value = "新增")
    void add(@Valid AddParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelRechargeDetailService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("采购订单ID")
        private Long purchaseId;
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("到账时间")
        private Date time;
        @ApiModelProperty("充值金额")
        private BigDecimal amount;
        @ApiModelProperty("充值条数")
        private Long count;
        @ApiModelProperty("充值单价")
        private BigDecimal price;
        @ApiModelProperty("实际单价")
        private BigDecimal actualPrice;
        @ApiModelProperty("充值备注")
        private String remark;
        @ApiModelProperty("创建人")
        private Long creatorId;
        @ApiModelProperty("类型")
        private NetType netType;
    }


    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {

        ChannelRechargeDetail copyProperties(AddParams params);
    }
}
