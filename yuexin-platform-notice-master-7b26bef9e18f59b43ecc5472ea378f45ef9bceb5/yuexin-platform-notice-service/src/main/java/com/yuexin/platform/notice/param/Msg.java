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
public class Msg implements Serializable {

    @ApiModelProperty("msg_type")
    @JSONField(name = "msg_type")
    private String msgType;

    @ApiModelProperty("card")
    private Card card;
}
