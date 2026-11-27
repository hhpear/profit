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
public class Content implements Serializable {

    @ApiModelProperty("file_key")
    @JSONField(name = "file_key")
    private String fileKey;

    @ApiModelProperty("content")
    private String content;

}
