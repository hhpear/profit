package com.yuexin.platform.notice.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.google.common.util.concurrent.RateLimiter;
import com.yuexin.BusinessException;
import com.yuexin.platform.notice.config.AppConfig;
import com.yuexin.platform.notice.param.*;
import com.yuexin.platform.notice.service.FeishuNoticeService;
import com.yuexin.platform.notice.util.FeishuUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author szy
 * @create 2023-03-06 11:51
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class FeishuNoticeServiceImpl implements FeishuNoticeService, InitializingBean {

    private final AppConfig appConfig;
    private final StringRedisTemplate redis;
    /**
     * 初始化群发消息限流器
     */
    private RateLimiter rateLimiter1;
    /**
     * 初始化单发消息限流器
     */
    private RateLimiter rateLimiter2;
    /**
     * 初始化机器人消息限流器
     */
    private RateLimiter rateLimiter3;

    private final static String redisKey1 = "platform:notice:feishu:queue";
    private final static String redisKey2 = "platform:notice:msg:queue";
    private final static String redisKey3 = "platform:notice:webhook:queue";

    private final static String MSG_TYPE_FILE = "file";
    private final static String MSG_TYPE_INTERACTIVE = "interactive";


    @Override
    public void sendCardMessage(CardParams params) throws BusinessException {
        redis.opsForList().leftPush(redisKey1, JSON.toJSONString(params));
    }

    @Override
    public void sendFileMessage(FileParams params) throws BusinessException {
        redis.opsForList().leftPush(redisKey2, JSON.toJSONString(params));
    }

    @Override
    public void sendWebhookCardMessage(WebhookCardParams params) throws BusinessException {
        redis.opsForList().leftPush(redisKey3, JSON.toJSONString(params));
    }

    @Override
    public void updateCardMessage(UpdateCardParams params) throws BusinessException {
        appConfig.getFeishuApps().stream()
                .filter(app -> app.getNoticeApp().equals(params.getApp())).findFirst()
                .ifPresent(app -> FeishuUtil.updateMsg(params.getMessageId(),
                        JSON.toJSONString(new Content().setContent(params.getContent())),
                        params.getToken()));
    }

    /**
     * 参数中的userId Message转成Json
     *
     * @param params
     * @return 参见发送消息的json形式
     */
    private String paramsToJson(CardParams params) {
        return JSON.toJSONString(new PersonalMsg()
                .setUserIds(params.getContactIds())
                .setMsgType(MSG_TYPE_INTERACTIVE)
                .setCard(toCard(params.getMessage())));
    }

    private String paramsToJson(String userId, String fileKey) {
        return JSON.toJSONString(new FileMsg().setReceiveId(userId)
                .setMsgType(MSG_TYPE_FILE)
                .setContent(JSON.toJSONString(new Content().setFileKey(fileKey))));
    }

    private Card toCard(Message message) {
        Header header = new Header(
                new Text(Text.PLAIN_TEXT, message.getTitle()),
                message.getTitleColor().toString().toLowerCase()
        );
        List<Element> elements = new ArrayList<>();
        // 表格形式
        if (CollectionUtils.isNotEmpty(message.getColumns()) && CollectionUtils.isNotEmpty(message.getRows())) {
            elements.add(toTable(message.getColumns(), message.getRows()));
            return new Card(header, elements);
        }
        // 子标题形式
        if (CollectionUtils.isNotEmpty(message.getSubtitles())) {
            List<Field> fields = new ArrayList<>();
            for (Subtitle subtitle : message.getSubtitles()) {
                fields.add(new Field(new Text(Text.LARK_MD, processSubtitle(2, "", subtitle))));
            }
            elements.add(new ElementDiv(fields));
        }
        // 交换按钮
        if (CollectionUtils.isNotEmpty(message.getButtons())) {
            elements.add(toButton(message.getButtons()));
        }
        return new Card(header, elements);
    }

    private Element toTable(List<Column> columns, JSONArray rows) {
        return new ElementTable(columns.stream()
                .map(c -> new TableColumn(c.getName(), c.getDisplay(), c.getWidth()))
                .collect(Collectors.toList()), rows);
    }

    private Element toButton(List<Button> buttons) {
        return new ElementColumn(ElementColumn.COLUMN_SET).setColumns(
                buttons.stream().map(button ->
                        new ElementColumn(ElementColumn.COLUMN).setElements(
                                Collections.singletonList(new ElementButton(new Text(Text.PLAIN_TEXT, button.getName()),
                                        Collections.singletonList(new Behavior(Behavior.OPEN_URL, button.getUrl())))))
                ).collect(Collectors.toList())
        );
    }


    /**
     * 递归拼接Json多层subtitle
     *
     * @param number   空格数
     * @param content  拼接过程的字符串
     * @param subtitle Subtitle
     * @return 拼接完成的Subtitle
     */
    private String processSubtitle(int number, String content, Subtitle subtitle) {

        if (subtitle == null) {
            return content;
        }
        String color = subtitle.getFontColor().toString().toLowerCase();

        if (subtitle.getName() != null) {
            if (subtitle.getBold()) {
                content += "<font color=\'" + color + "\'>&#8226;" + "    </font>**<font color=\'" + color + "\'>";
                content += (subtitle.getContent() != "") ? (subtitle.getName() + "：" + "</font>" + "**") : (subtitle.getName() + "</font>" + "**");
            } else {
                content += "<font color=\'" + color + "\'>&#8226;" + "    </font><font color=\'" + color + "\'>";
                content += (subtitle.getContent() != "") ? (subtitle.getName() + "：</font>") : (subtitle.getName() + "</font>");
            }
            if (subtitle.getContent() != "") {
                content += "<font color=\'" + color + "\'>" + subtitle.getContent() + "</font>";
            }
        } else {
            content += "<font color=\'" + color + "\'>&#8226;" + "    " + subtitle.getContent() + "</font>";
        }

        List<Subtitle> items = subtitle.getItems();
        if (items != null) {
            for (Subtitle item : items) {
                content += "\n" + blanks(number);
                content = processSubtitle(number + 1, content, item);
            }
        }
        return content;

    }

    /**
     * 每层subtitle前的空格
     *
     * @param number
     * @return
     */
    private String blanks(int number) {
        String blank = "";
        for (int i = 0; i < number * 3 + (number - 2) * 3; i++) {
            blank += " ";
        }
        return blank;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        rateLimiter1 = RateLimiter.create(appConfig.getFeishuRateLimit());
        new Thread(() -> {
            while (true) {
                sendCardMessage();
            }
        }, "feishu-notice-thread").start();
        log.info("飞书群发通知线程启动");

        rateLimiter2 = RateLimiter.create(5);
        new Thread(() -> {
            while (true) {
                sendFileMessage();
            }
        }, "msg-notice-thread").start();
        log.info("飞书文件通知线程启动");

        rateLimiter3 = RateLimiter.create(1);
        new Thread(() -> {
            while (true) {
                sendWebhookCardMessage();
            }
        }, "webhook-notice-thread").start();
        log.info("飞书webhook通知线程启动");
    }

    @SneakyThrows(InterruptedException.class)
    private void sendCardMessage() {
        String result = redis.opsForList().rightPop(redisKey1);
        if (StringUtils.isBlank(result)) {
            Thread.sleep(500);
            return;
        }
        CardParams params = JSON.parseObject(result, CardParams.class);
        rateLimiter1.acquire();
        try {
            appConfig.getFeishuApps().stream()
                    .filter(app -> app.getNoticeApp().equals(params.getApp())).findFirst()
                    .ifPresent(app -> FeishuUtil.postGroupMsg(paramsToJson(params), FeishuUtil.getToken(app.getAppId(), app.getAppSecret())));
        } catch (Exception e) {
            log.error("飞书群发通知失败, \n{}", params, e);
        }
    }

    @SneakyThrows(InterruptedException.class)
    public void sendFileMessage() {
        String result = redis.opsForList().rightPop(redisKey2);
        if (StringUtils.isBlank(result)) {
            Thread.sleep(500);
            return;
        }
        FileParams params = JSON.parseObject(result, FileParams.class);
        rateLimiter2.acquire();
        try {
            appConfig.getFeishuApps().stream()
                    .filter(app -> app.getNoticeApp().equals(params.getApp())).findFirst()
                    .ifPresent(app -> params.getContactIds().forEach(userId ->
                            FeishuUtil.sendMsg(paramsToJson(userId, params.getFileKey()),
                                    FeishuUtil.getToken(app.getAppId(), app.getAppSecret()))
                    ));
        } catch (Exception e) {
            log.error("飞书文件通知失败, \n{}", params, e);
        }
    }

    @SneakyThrows(InterruptedException.class)
    public void sendWebhookCardMessage() {
        String result = redis.opsForList().rightPop(redisKey3);
        if (StringUtils.isBlank(result)) {
            Thread.sleep(500);
            return;
        }
        WebhookCardParams params = JSON.parseObject(result, WebhookCardParams.class);
        rateLimiter3.acquire();
        try {
            FeishuUtil.sendWebhook(params.getWebhookUrl(), JSON.toJSONString(new Msg().setMsgType(MSG_TYPE_INTERACTIVE).setCard(toCard(params.getMessage()))));
        } catch (Exception e) {
            log.error("飞书webhook通知失败, \n{}", params, e);
        }
    }
}
