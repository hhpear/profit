package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.bmp.bi.common.AlarmConfig;
import com.yuexin.bmp.bi.common.ChannelBalanceConfig;
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
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * @author lxf
 * @since 2025-03-26 14:12:56
 */
@Validated
public interface ChannelBalanceConfigService {

    @ApiOperation(value = "查询")
    List<ChannelBalanceConfig> multiGet(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查看全局配置")
    ChannelBalanceConfig getGlobal(@Valid GetParams params) throws BusinessException;

    @ApiOperation(value = "查看")
    ChannelBalanceConfig getExact(@Valid GetParams params) throws BusinessException;

    @ApiOperation(value = "新增")
    void add(@Valid AddParams params) throws BusinessException;

    @ApiOperation(value = "修改")
    void modify(@Valid ModifyParams params) throws BusinessException;

    @ApiOperation(value = "删除")
    void delete(@Valid DeleteParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceConfigService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty("通道ID")
        private List<Long> channelIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceConfigService.GetParams")
    class GetParams implements Serializable {
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("类型")
        @NotNull
        private NetType netType;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceConfigService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("类型")
        private NetType netType;
        @ApiModelProperty("配置明细")
        private List<AlarmConfig> configs;
        @ApiModelProperty("操作人")
        private Long operatorId;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceConfigService.ModifyParams")
    class ModifyParams implements Serializable {
        @ApiModelProperty("主键")
        private Long id;
        @ApiModelProperty("配置明细")
        private List<AlarmConfig> configs;
        @ApiModelProperty("操作人")
        private Long operatorId;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceConfigService.DeleteParams")
    class DeleteParams implements Serializable {
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("类型")
        private NetType netType;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        ChannelBalanceConfig copyProperties(AddParams params);

        ChannelBalanceConfig copyProperties(ModifyParams params);
    }
}
