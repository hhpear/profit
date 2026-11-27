package com.yuexin.platform.notice.param;

import com.yuexin.platform.notice.model.NoticeApp;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * @author szy
 * @create 2023-03-07 10:01
 */
@ApiModel("飞书应用")
@Getter
@Setter
@Accessors(chain = true)
public class Application implements Serializable {

    @ApiModelProperty(value = "应用名称", required = true)
    @NotNull
    private NoticeApp noticeApp;

    @ApiModelProperty(value = "飞书ID", required = true)
    @NotNull
    private String appId;

    @ApiModelProperty(value = "飞书密码", required = true)
    @NotNull
    private String appSecret;
}
