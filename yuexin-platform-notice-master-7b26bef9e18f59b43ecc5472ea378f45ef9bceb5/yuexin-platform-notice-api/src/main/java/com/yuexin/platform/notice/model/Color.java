package com.yuexin.platform.notice.model;

import de.pentabyte.springfox.ApiEnum;
import io.swagger.annotations.ApiModel;

/**
 * @author szy
 * @create 2023-03-06 11:18
 */
@ApiModel("字体颜色")
public enum Color {

    @ApiEnum("蓝色")
    Blue,

    @ApiEnum("浅蓝色")
    Wathet,

    @ApiEnum("青绿色")
    Turquoise,

    @ApiEnum("绿色")
    Green,

    @ApiEnum("黄色")
    Yellow,

    @ApiEnum("橘色")
    Orange,

    @ApiEnum("红色")
    Red,

    @ApiEnum("洋红色")
    Carmine,

    @ApiEnum("蓝紫色")
    Violet,

    @ApiEnum("紫色")
    Purple,

    @ApiEnum("靛蓝色")
    Indigo,

    @ApiEnum("灰色")
    Grey,

    @ApiEnum("黑色")
    Black,

    @ApiEnum("白色")
    White
}
