package com.yuexin.platform.notice.param;

import com.yuexin.platform.notice.model.Color;
import com.yuexin.validation.MultipleEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @author szy
 * @create 2023-03-06 11:38
 */
@ApiModel("子标题")
@Getter
@Setter
@Accessors(chain = true)
public class Subtitle implements Serializable {

    @ApiModelProperty(value = "标题名称", required = true)
    private String name;

    @ApiModelProperty(value = "字体颜色", required = true)
    @MultipleEnum(values = {"Green","Grey","Red","Black",""},
            message = "字体不支持此颜色")
    private Color fontColor = Color.Black;

    @ApiModelProperty(value = "是否加粗", required = true)
    private Boolean bold = false;

    @ApiModelProperty(value = "内容", required = true)
    private String content = "";

    @ApiModelProperty(value = "元素内容", required = true)
    private List<Subtitle> items;
}

