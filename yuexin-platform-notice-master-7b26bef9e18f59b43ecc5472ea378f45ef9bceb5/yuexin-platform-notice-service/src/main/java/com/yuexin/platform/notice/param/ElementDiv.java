package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class ElementDiv extends Element implements Serializable {

    private final static String DIV = "div";

    @ApiModelProperty("fields")
    private List<Field> fields;

    public ElementDiv(List<Field> fields) {
        super(DIV);
        this.fields = fields;
    }
}
