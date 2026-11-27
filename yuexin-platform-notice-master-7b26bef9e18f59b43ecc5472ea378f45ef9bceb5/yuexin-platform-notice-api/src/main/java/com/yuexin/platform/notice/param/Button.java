package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author szy
 * @create 2023-03-06 11:38
 */
@ApiModel("子标题")
@Getter
@Setter
@Accessors(chain = true)
public class Button implements Serializable {

    @ApiModelProperty(value = "按钮名称", required = true)
    private String name;

    @ApiModelProperty(value = "跳转地址", required = true)
    private String url;
}

