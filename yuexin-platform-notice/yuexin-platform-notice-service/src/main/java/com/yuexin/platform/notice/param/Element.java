package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class Element implements Serializable {

    @ApiModelProperty("tag")
    private String tag;

    public Element(String tag) {
        this.tag = tag;
    }

}
