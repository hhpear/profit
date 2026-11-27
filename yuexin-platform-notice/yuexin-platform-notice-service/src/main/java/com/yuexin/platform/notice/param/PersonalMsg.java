package com.yuexin.platform.notice.param;

import com.alibaba.fastjson.annotation.JSONField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Set;

@Getter
@Setter
@Accessors(chain = true)
public class PersonalMsg extends Msg implements Serializable {

    @ApiModelProperty("飞书ID")
    @JSONField(name = "user_ids")
    private Set<String> userIds;
}
