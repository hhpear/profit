package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.ChannelSyncDetail;
import com.yuexin.bmp.bi.common.NetType;
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
 * @since 2024-11-20 13:45:23
 */
@Validated
public interface ChannelSyncDetailService {

    @ApiOperation(value = "查询")
    PageResponse<ChannelSyncDetail> query(QueryParams params) throws BusinessException;

    @ApiOperation(value = "时间分组查询")
    List<ChannelSyncDetail> queryGroup(QueryParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelSyncDetailService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("分页参数")
        private PageParams page;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("同步时间开始")
        private Date startTime;
        @ApiModelProperty("同步时间结束")
        private Date endTime;
        @ApiModelProperty("类型")
        private NetType netType;
    }

    @ApiOperation(value = "新增")
    void add(@Valid AddParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelSyncDetailService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("通道编码")
        private String channelCode;
        @ApiModelProperty("同步时间")
        private Date time;
        @ApiModelProperty("运营商余额")
        private BigDecimal oppBalance;
        @ApiModelProperty("我方余额")
        private BigDecimal ourBalance;
        @ApiModelProperty("差异")
        private BigDecimal difference;
        @ApiModelProperty("余额同步截图")
        private List<String> syncUrls;
        @ApiModelProperty("余额同步备注")
        private String remark;
        @ApiModelProperty("创建人")
        private Long creatorId;
        @ApiModelProperty("创建时间")
        private Date createTime;
        @ApiModelProperty("类型")
        private NetType netType;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {

        ChannelSyncDetail copyProperties(AddParams params);
    }
}
