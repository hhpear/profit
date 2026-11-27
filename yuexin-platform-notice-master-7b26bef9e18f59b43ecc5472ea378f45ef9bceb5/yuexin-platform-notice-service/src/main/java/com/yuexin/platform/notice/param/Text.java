package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class Text implements Serializable {

    public final static String PLAIN_TEXT = "plain_text";
    public final static String LARK_MD = "lark_md";

    @ApiModelProperty("tag")
    private String tag;

    @ApiModelProperty("content")
    private String content;

    public Text(String tag, String content) {
        this.tag = tag;
        this.content = content;
    }
}
