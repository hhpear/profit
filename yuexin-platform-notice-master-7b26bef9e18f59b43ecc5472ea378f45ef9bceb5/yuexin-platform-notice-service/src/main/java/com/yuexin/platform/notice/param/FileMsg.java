package com.yuexin.platform.notice.param;

import com.alibaba.fastjson.annotation.JSONField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class FileMsg implements Serializable {

    @ApiModelProperty("飞书ID")
    @JSONField(name = "receive_id")
    private String receiveId;

    @ApiModelProperty("msg_type")
    @JSONField(name = "msg_type")
    private String msgType;

    @ApiModelProperty("content")
    @JSONField(name = "content")
    private String content;
}
