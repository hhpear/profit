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
public class Card implements Serializable {

    @ApiModelProperty("header")
    private Header header;

    @ApiModelProperty("elements")
    private List<Element> elements;

    public Card(Header header, List<Element> elements) {
        this.header = header;
        this.elements = elements;
    }
}
