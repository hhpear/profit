package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class Header implements Serializable {

    @ApiModelProperty("title")
    private Text title;

    @ApiModelProperty("template")
    private String template;

    public Header(Text title, String template) {
        this.title = title;
        this.template = template;
    }
}
