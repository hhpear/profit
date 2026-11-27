package com.yuexin.platform.notice.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * @author lxf
 * @date 2024/11/13
 */
@ApiModel("表格列")
@Getter
@Setter
@Accessors(chain = true)
public class Column implements Serializable {

    @ApiModelProperty(value = "自定义列的标记", required = true)
    @NotBlank
    private String name;

    @ApiModelProperty(value = "在表头展示的列名称", required = true)
    @NotBlank
    private String display;

    @ApiModelProperty("宽度")
    private Integer width;
}

