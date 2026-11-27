package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class Field implements Serializable {

    @ApiModelProperty("text")
    private Text text;

    public Field(Text text) {
        this.text = text;
    }
}
