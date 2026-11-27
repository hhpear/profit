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
public class HeaderStyle implements Serializable {

    @ApiModelProperty("表头文本对齐方式可取值：" +
            "left：左对齐" +
            "center：居中对齐" +
            "right：右对齐")
    @JSONField(name = "text_align")
    private String textAlign = "center";

    @ApiModelProperty("表头文本大小。可取值：" +
            "normal：正文（14px）" +
            "heading：标题（16px）")
    @JSONField(name = "text_size")
    private String textSize = "heading";

    @ApiModelProperty("表头背景色。可取值：" +
            "grey：灰色" +
            "none：无背景色")
    @JSONField(name = "background_style")
    private String backgroundStyle = "none";

    @ApiModelProperty("文本颜色。可取值：" +
            "default：客户端浅色主题模式下为黑色；客户端深色主题模式下为白色" +
            "grey：灰色")
    @JSONField(name = "text_color")
    private String textColor = "default";

    @ApiModelProperty("表头文本是否加粗")
    private Boolean bold = true;

    @ApiModelProperty("表头文本的行数")
    private Integer lines = 1;
}
