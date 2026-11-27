package com.yuexin.platform.notice.param;

import com.alibaba.fastjson.JSONArray;
import com.yuexin.platform.notice.model.Color;
import com.yuexin.validation.MultipleEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * @author szy
 * @create 2023-03-06 11:35
 */
@ApiModel("消息")
@Getter
@Setter
@Accessors(chain = true)
public class Message implements Serializable {

    @ApiModelProperty(value = "标题名称", required = true)
    @NotNull
    private String title;

    @ApiModelProperty(value = "标题背景颜色", required = true)
    @MultipleEnum(values = {"Blue", "Wathet", "Turquoise", "Green", "Yellow", "Orange",
            "Red", "Carmine", "Violet", "Purple", "Indigo", "Grey", "White", ""},
            message = "标题背景不支持此颜色")
    private Color titleColor = Color.White;

    @ApiModelProperty(value = "子标题")
    private List<Subtitle> subtitles;

    @ApiModelProperty(value = "交互按钮")
    private List<Button> buttons;

    @ApiModelProperty(value = "表格：表头")
    private List<Column> columns;

    @ApiModelProperty(value = "表格：数据")
    private JSONArray rows;

}
