package com.yuexin.platform.notice.model;

import de.pentabyte.springfox.ApiEnum;
import io.swagger.annotations.ApiModel;

/**
 * @author szy
 * @create 2023-04-12 13:50
 */
@ApiModel("报警级别")
public enum AlarmLevel {

    @ApiEnum("严重")
    Critical,

    @ApiEnum("高")
    High,

    @ApiEnum("一般")
    Normal,

    @ApiEnum("报警恢复")
    Recovery

}
