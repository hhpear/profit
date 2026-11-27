package com.yuexin.platform.notice.model;

import de.pentabyte.springfox.ApiEnum;
import io.swagger.annotations.ApiModel;

/**
 * @author szy
 * @create 2023-03-06 11:17
 */
@ApiModel("飞书应用")
public enum NoticeApp {

    @ApiEnum("业务告警应用")
    BusinessAlarm,

    @ApiEnum("业务中台")
    BMP
}
