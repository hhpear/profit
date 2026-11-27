package com.yuexin.platform.notice.service;

import com.yuexin.BusinessException;
import com.yuexin.platform.notice.model.AlarmLevel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;

/**
 * @author szy
 * @create 2023-04-12 12:03
 */
@ApiModel("发送监控大屏消息")
@Validated
public interface ScreenNoticeService {

    @ApiOperation("发送报警大屏消息")
    void sendScreenMessage(@Valid MessageParams params) throws BusinessException, IOException;


    @ApiModel(value = "ScreenNoticeService.MessageParams", description = "参数：发送报警大屏消息")
    @Getter
    @Setter
    @Accessors(chain = true)
    class MessageParams implements Serializable {
        @ApiModelProperty(value = "平台代码", required = true)
        @NotNull
        private String platform;

        @ApiModelProperty(value = "报警ID")
        private String alarmId;

        @ApiModelProperty(value = "报警名称")
        private String name;

        @ApiModelProperty(value = "实例名称")
        private String instanceName;

        @ApiModelProperty(value = "监控项名称")
        private String metricName;

        @ApiModelProperty(value = "报警值（包括单位）")
        private String alarmValue;

        @ApiModelProperty(value = "开始报警时间，时戳（秒）")
        private Long alarmTime;

        @ApiModelProperty(value = "持续时间，秒")
        private Long duration;

        @ApiModelProperty(value = "报警级别")
        private AlarmLevel alarmLevel;

    }
}
