package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.io.Serializable;


/**
 * @author lixiaofeng
 * @date 2025/01/03
 */
@Validated
public interface BalanceUpdateService {

    @ApiOperation(value = "更新")
    void update(@Valid UpdateParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelBalanceService.UpdateParams")
    class UpdateParams implements Serializable {
        @ApiModelProperty("通道编码")
        private String channelCode;
    }
}
