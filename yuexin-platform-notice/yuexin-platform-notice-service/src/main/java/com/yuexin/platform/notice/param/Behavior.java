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
public class Behavior implements Serializable {

    public final static String OPEN_URL = "open_url";

    @ApiModelProperty("tag")
    private String type;

    @ApiModelProperty("default_url")
    @JSONField(name = "default_url")
    private String url;

    public Behavior(String type, String url) {
        this.type = type;
        this.url = url;
    }
}
