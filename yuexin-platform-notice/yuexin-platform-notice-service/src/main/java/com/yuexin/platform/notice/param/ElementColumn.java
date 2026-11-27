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
public class ElementColumn extends Element implements Serializable {

    public final static String COLUMN_SET = "column_set";
    public final static String COLUMN = "column";

    @ApiModelProperty("columns")
    private List<ElementColumn> columns;

    @ApiModelProperty("elements")
    private List<ElementButton> elements;

    public ElementColumn(String tag) {
        super(tag);
    }
}
