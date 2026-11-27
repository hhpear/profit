package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.ChannelBalance;
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
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author lxf
 * @since 2024-11-20 13:46:14
 */
@Validated
public interface ChannelBalanceService {

    @ApiOperation(value = "查询")
    PageResponse<ChannelBalance> query(QueryParams params) throws BusinessException;

    PageResponse<ChannelBalance> groupQuery(QueryParams params) throws BusinessException;

    ChannelBalance sum(QueryParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("通道ID")
        private List<Long> channelIds;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("通道名称")
        private String channelName;
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("供应商ID")
        private List<Long> supplierIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID")
        private List<Long> businessBodyIds;
        @ApiModelProperty("接入号")
        private String spNumber;
        @ApiModelProperty("落地省份")
        private String landProvince;
        @ApiModelProperty("落地地市")
        private String landCity;
        @ApiModelProperty("代理商名称")
        private String agentName;
        @ApiModelProperty("代理商名称")
        private List<String> agentNames;
        @ApiModelProperty("近30天发送情况")
        private Boolean sendInfo;
        @ApiModelProperty("日期")
        private Date createTime;
        @ApiModelProperty("分组字段")
        private List<String> groups;
    }

    @ApiOperation(value = "查看")
    ChannelBalance get(@Valid GetParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceService.GetParams")
    class GetParams implements Serializable {
        @ApiModelProperty("通道编码")
        @NotBlank
        private String channelCode;
        @ApiModelProperty("日期")
        private Date createTime;
    }

    @ApiOperation(value = "新增")
    void add(@Valid AddParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("日期")
        private Date createTime;
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("通道名称")
        private String channelName;
        @ApiModelProperty("供应商ID")
        private Long supplierId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("接入号")
        private String spNumber;
        @ApiModelProperty("落地省份")
        private String landProvince;
        @ApiModelProperty("落地地市")
        private String landCity;
        @ApiModelProperty("代理商名称")
        private String agentName;
        @ApiModelProperty("到账金额")
        private BigDecimal dzAmount = BigDecimal.ZERO;
        @ApiModelProperty("在途金额")
        private BigDecimal ztAmount = BigDecimal.ZERO;
        @ApiModelProperty("账单金额")
        private BigDecimal zdAmount = BigDecimal.ZERO;
        @ApiModelProperty("全网剩余金额")
        private BigDecimal allAmount = BigDecimal.ZERO;
        @ApiModelProperty("全网实际剩余金额")
        private BigDecimal allActualAmount = BigDecimal.ZERO;
        @ApiModelProperty("省网实际剩余金额")
        private BigDecimal poActualAmount = BigDecimal.ZERO;
        @ApiModelProperty("订单条数")
        private Long dzCount = 0L;
        @ApiModelProperty("账单条数")
        private Long zdCount = 0L;
        @ApiModelProperty("全网剩余条数")
        private Long allCount = 0L;
        @ApiModelProperty("省网剩余条数")
        private Long poCount = 0L;
        @ApiModelProperty("近一月日均发送量(全网)")
        private Long allAvgCount = 0L;
        @ApiModelProperty("近一月日均发送量(省网)")
        private Long poAvgCount = 0L;
        @ApiModelProperty("预计可发送天数(全网)")
        private Integer allPreDays = 0;
        @ApiModelProperty("预计可发送天数(省网)")
        private Integer poPreDays = 0;
        @ApiModelProperty("是否展示余额")
        private Boolean balanceShow;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {

        ChannelBalance copyProperties(AddParams params);

        List<ChannelBalance> copyProperties(List<AddParams> params);
    }
}
