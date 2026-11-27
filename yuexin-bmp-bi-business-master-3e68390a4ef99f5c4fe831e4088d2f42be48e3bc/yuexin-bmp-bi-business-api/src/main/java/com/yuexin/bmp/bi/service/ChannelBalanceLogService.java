package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.ChannelBalanceLog;
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
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author lxf
 * @since 2025-03-26 14:13:12
 */
@Validated
public interface ChannelBalanceLogService {

    @ApiOperation(value = "查询")
    ChannelBalanceLog get(@Valid GetParams params) throws BusinessException;

    @ApiOperation(value = "查询")
    List<ChannelBalanceLog> multiGet(@Valid MultiGetParams params) throws BusinessException;

    @ApiOperation(value = "批量新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceLogService.GetParams")
    class GetParams implements Serializable {
        @ApiModelProperty("通道ID")
        @NotNull
        private Long channelId;
        @ApiModelProperty("类型")
        @NotNull
        private NetType netType;
        @ApiModelProperty("是否报警")
        @NotNull
        private Boolean state;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceLogService.QueryParams")
    class MultiGetParams implements Serializable {
        @ApiModelProperty("通道ID")
        @NotEmpty
        private List<Long> channelIds;
        @ApiModelProperty("报警时间-开始")
        @NotNull
        private Date startTime;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceLogService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("类型")
        private NetType netType;
        @ApiModelProperty("配置ID")
        private Long configId;
        @ApiModelProperty("报警档位")
        private Integer alarmLevel;
        @ApiModelProperty("报警值")
        private Long alarmValue;
        @ApiModelProperty("备注信息")
        private String remark;
        @ApiModelProperty("是否报警")
        private Boolean state;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        ChannelBalanceLog copyProperties(AddParams params);
    }
}
