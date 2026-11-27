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
public class ElementButton extends Element implements Serializable {

    private final static String BUTTON = "button";

    @ApiModelProperty("text")
    private Text text;

    @ApiModelProperty("behaviors")
    private List<Behavior> behaviors;

    public ElementButton(Text text, List<Behavior> behaviors) {
        super(BUTTON);
        this.text = text;
        this.behaviors = behaviors;
    }
}
