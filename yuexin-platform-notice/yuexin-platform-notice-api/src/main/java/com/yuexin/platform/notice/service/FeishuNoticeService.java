package com.yuexin.platform.notice.service;

import com.yuexin.BusinessException;
import com.yuexin.platform.notice.model.NoticeApp;
import com.yuexin.platform.notice.param.Message;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

/**
 * @author szy
 * @create 2023-03- 11:11
 */
@ApiModel("飞书发送消息")
@Validated
public interface FeishuNoticeService {

    @ApiOperation("发送card消息")
    void sendCardMessage(@Valid CardParams params) throws BusinessException;

    @ApiOperation("发送file消息")
    void sendFileMessage(@Valid FileParams params) throws BusinessException;

    @ApiOperation("通过Webhook发送card消息")
    void sendWebhookCardMessage(@Valid WebhookCardParams params) throws BusinessException;

    @ApiOperation("更新消息卡片")
    void updateCardMessage(@Valid UpdateCardParams params) throws BusinessException;

    @ApiModel(value = "FeishuNoticeService.CardParams", description = "参数：发送card消息")
    @Getter
    @Setter
    @Accessors(chain = true)
    class CardParams implements Serializable {
        @ApiModelProperty(value = "飞书应用", required = true)
        @NotNull
        private NoticeApp app;

        @ApiModelProperty(value = "联系人Id")
        private Set<String> contactIds;

        @ApiModelProperty(value = "消息")
        @Valid
        private Message message;

    }

    @ApiModel(value = "FeishuNoticeService.FileParams", description = "参数：发送file消息")
    @Getter
    @Setter
    @Accessors(chain = true)
    class FileParams implements Serializable {
        @ApiModelProperty(value = "飞书应用", required = true)
        @NotNull
        private NoticeApp app;
        @ApiModelProperty(value = "联系人Id")
        private Set<String> contactIds;
        @ApiModelProperty(value = "文件key")
        private String fileKey;
    }


    @ApiModel(value = "FeishuNoticeService.WebhookCardParams", description = "参数：发送card消息")
    @Getter
    @Setter
    @Accessors(chain = true)
    class WebhookCardParams implements Serializable {

        @NotBlank
        @ApiModelProperty(value = "webhook地址")
        private String webhookUrl;

        @ApiModelProperty(value = "消息")
        @Valid
        private Message message;

    }

    @ApiModel(value = "FeishuNoticeService.UpdateCardParams", description = "参数：更新消息卡片")
    @Getter
    @Setter
    @Accessors(chain = true)
    class UpdateCardParams implements Serializable {
        @ApiModelProperty(value = "飞书应用", required = true)
        @NotNull
        private NoticeApp app;
        @NotBlank
        @ApiModelProperty(value = "待更新的消息ID 示例值：om_dc13264520392913993dd051dba21dcf")
        private String messageId;
        @ApiModelProperty(value = "卡片内容 ")
        @NotBlank
        private String content;
        @ApiModelProperty(value = "token ")
        @NotBlank
        private String token;

    }
}
