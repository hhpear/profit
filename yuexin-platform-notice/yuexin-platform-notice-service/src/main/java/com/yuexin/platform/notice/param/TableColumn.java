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
public class TableColumn implements Serializable {

    @ApiModelProperty("自定义列的标记")
    private String name;

    @ApiModelProperty("在表头展示的列名称")
    @JSONField(name = "display_name")
    private String displayName;

    @ApiModelProperty("列宽度")
    private String width;

    @ApiModelProperty("列内数据垂直对齐方式。可选值：" +
            "top：顶部对齐" +
            "center：中间对齐" +
            "bottom：底部对齐")
    @JSONField(name = "vertical_align")
    private String verticalAlign = "center";

    @ApiModelProperty("列内数据水平对齐方式。可选值：" +
            "left：左对齐" +
            "center：居中对齐" +
            "right：右对齐")
    @JSONField(name = "horizontal_align")
    private String horizontalAlign = "center";

    @ApiModelProperty("列数据类型。可选值如下所示。" +
            "text：不带格式的普通文本。为 data_type 默认值。" +
            "lark_md：支持部分 markdown 格式的文本。飞书 v7.10 及之后版本支持。详情参考普通文本-lark_md 支持的 Markdown 语法" +
            "options：选项标签" +
            "number：数字。默认在单元格中右对齐展示。若选择该数据类型，你可继续在 column 中添加 format 字段，设置数字的格式属性" +
            "persons：人员列表。为用户名称+头像样式" +
            "date：日期时间。需输入 Unix 标准毫秒级时间戳，飞书客户端将按用户本地时区展示日期时间。飞书 v7.6 及之后版本支持" +
            "markdown：支持完整 Markdown 语法的文本内容。详情参考富文本（Markdown）组件。飞书 v7.14 及之后版本支持")
    @JSONField(name = "data_type")
    private String dataType = "lark_md";

    public TableColumn(String name, String displayName, Integer width) {
        this.name = name;
        if (width == null) {
            this.width = "auto";
        } else {
            this.width = width + "px";
        }
        this.displayName = displayName;
    }

}
