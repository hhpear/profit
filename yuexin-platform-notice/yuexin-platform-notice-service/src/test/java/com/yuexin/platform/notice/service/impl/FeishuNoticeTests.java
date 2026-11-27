package com.yuexin.platform.notice.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.yuexin.BusinessException;
import com.yuexin.platform.notice.model.Color;
import com.yuexin.platform.notice.param.*;
import com.yuexin.platform.notice.service.FeishuNoticeService;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.*;

import static com.yuexin.platform.notice.model.Color.Red;
import static com.yuexin.platform.notice.model.NoticeApp.BMP;
import static com.yuexin.platform.notice.model.NoticeApp.BusinessAlarm;

/**
 * @author szy
 * @create 2023-03-06 13:55
 */
@DisplayName("飞书发送消息")
@SpringBootTest
@ExtendWith(SpringExtension.class)
@Slf4j
public class FeishuNoticeTests {

    @Setter
    private List<Application> applications;

    @DubboReference
    private FeishuNoticeService feishuNoticeService;

//    @Value("{$feishu-app}")
//    private String applicationList;


    @Test
    public void test() throws BusinessException {
        FeishuNoticeService.FileParams fileParams = new FeishuNoticeService.FileParams();
        Set<String> userIds = new HashSet<>();
        userIds.add("a5g25327");
        fileParams.setContactIds(userIds);
        fileParams.setApp(BMP);
        fileParams.setFileKey("file_v3_00fo_89bf8ada-2405-4e32-976f-a705c9fb55fg");

        feishuNoticeService.sendFileMessage(fileParams);
    }

    @Test
    @DisplayName("发消息")
    public void testPost() throws IOException, BusinessException {

        FeishuNoticeService.CardParams cardParams = new FeishuNoticeService.CardParams();
        Set<String> userIds = new HashSet<>();
        userIds.add("a5g25327");
        List<Subtitle> subtitles = new ArrayList<>();
        //一条
        List<Subtitle> list2 = new ArrayList<>();
        Subtitle subtitle2 = new Subtitle()
                .setBold(true)
                .setContent("[Feishu Open Platform](https://open.feishu.cn/open-apis/approval/v4/instances/9188463E-778B-454D-B10A-CD1FA3AF9D54)")
                .setName("账号")
                .setFontColor(Red)
                .setItems(list2);
        subtitles.add(subtitle2);
        Message message = new Message();
        message.setTitle("【报警】账号QPS超速报警")
                .setTitleColor(Red)
                .setSubtitles(subtitles);
        cardParams.setApp(BusinessAlarm)
                .setContactIds(userIds)
                .setMessage(message);


        feishuNoticeService.sendCardMessage(cardParams);


    }

    @Test
    public void testWebhook() throws BusinessException {
        List<Subtitle> subtitles = new ArrayList<>();
        //Subtitle第一层
        Subtitle subtitle1 = new Subtitle()
                .setBold(true)
                .setName("指标当前值")
                .setContent("测试");

        subtitles.add(subtitle1);
        subtitles.add(new Subtitle().setContent("<at id=all>所有人</at>"));
        Message message = new Message();

        List<Button> buttons = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        buttons.add(new Button().setName("取消@所有人1").setUrl("http://39.98.72.59:30081/webhook/pause?redisKey=test:ttt&minute=10"));

        message.setTitle("【报警】账号QPS超速报警")
                .setTitleColor(Red)
                .setSubtitles(subtitles)
                .setButtons(buttons);

        FeishuNoticeService.WebhookCardParams mock = new FeishuNoticeService.WebhookCardParams();
        mock.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/ffee49e6-28a2-447e-8d07-ad088849d89e");
        mock.setMessage(message);
        feishuNoticeService.sendWebhookCardMessage(mock);
    }

    @Test
    public void testTableWebhook() throws BusinessException {

        List<testObject> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            testObject obj = new testObject();
            obj.setProvinceCity("测试" + i);
            obj.setSpNumber("测试" + i);
            String amount = "测试" + i;
            obj.setAmount(setColor(Red, amount));
            String feishuId = "a5g25327";
            String balance = "<at id=" + feishuId + ">所有人</at>";

            obj.setBalance(balance);
            list.add(obj);
        }
        Message message = new Message();
        message.setTitle("测试表格")
                .setTitleColor(Red)
                .setColumns(Arrays.asList(new Column().setName("provinceCity").setDisplay("落地省份地市"),
                        new Column().setName("spNumber").setDisplay("端口号"),
                        new Column().setName("amount").setDisplay("当前账户余额(元)").setWidth(145),
                        new Column().setName("balance").setDisplay("订包后预计账户余额(元)").setWidth(190)))
                .setRows(JSONArray.parseArray(JSON.toJSONString(list)));

        FeishuNoticeService.WebhookCardParams mock = new FeishuNoticeService.WebhookCardParams();
        mock.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/ffee49e6-28a2-447e-8d07-ad088849d89e");
        mock.setMessage(message);
        feishuNoticeService.sendWebhookCardMessage(mock);
    }

    private String setColor(Color color, String value) {
        if (color != null && value != null) {
            value = "<font color='" + color.toString().toLowerCase() + "'>" + value + "</font>";
        }
        return value;
    }


    @Getter
    @Setter
    public static class testObject {
        @ApiModelProperty("落地省份地市")
        private String provinceCity;
        @ApiModelProperty("端口号")
        private String spNumber;
        @ApiModelProperty("当前账户余额(元)")
        private String amount;
        @ApiModelProperty("订包后预计账户余额(元)")
        private String balance;
    }

    @Test
    public void testUpdate() throws BusinessException {
        FeishuNoticeService.UpdateCardParams mock = new FeishuNoticeService.UpdateCardParams();
        mock.setApp(BusinessAlarm);
        mock.setMessageId("om_413f95c03008cad8cb577bd0979c76c3");
        mock.setContent("{\"elements\":[{\"fields\":[{\"text\":{\"content\":\"<font color='red'>&#8226;    </font>**<font color='red'>账号：</font>**<font color='red'>测试aaa</font>\",\"tag\":\"lark_md\"}}],\"tag\":\"div\"}],\"header\":{\"template\":\"red\",\"title\":{\"content\":\"【报警】账号QPS超速报警\",\"tag\":\"plain_text\"}}}");
        mock.setToken("t-g104alalEGYJHWITZAYF6X4KOPXCQ4U7B5BF4TUD");
        feishuNoticeService.updateCardMessage(mock);
    }


//    @Test
//    @DisplayName("发复杂消息")
//    public void testPostDiff() throws IOException, BusinessException {
//
//        FeishuNoticeService.CardParams cardParams = new FeishuNoticeService.CardParams();
//        Set<String> userIds = new HashSet<>();
//        userIds.add("bc5a6826");
//        userIds.add("28e86cc7");
//
//        List<Subtitle> subtitles = new ArrayList<>();
//
//        //一条
//        List<String> list = new ArrayList<>();
//        Subtitle subtitle = new Subtitle()
//                .setBold(true)
//                .setContent("alnmwl")
//                .setName("账号")
//                .setFontColor(Red)
//                .setItems(list);
//        subtitles.add(subtitle);
//
//        //一条
//        List<String> list1 = new ArrayList<>();
//        list1.add("小时提交量 = 0条");
//        Subtitle subtitle1 = new Subtitle()
//                .setBold(true)
//                .setName("持续时间")
//                .setItems(list1);
//        subtitles.add(subtitle1);
//
//        //一条
//        List<String> list2 = new ArrayList<>();
//        list2.add("小时提交量 = 0条");
//        Subtitle subtitle2 = new Subtitle()
//                .setBold(true)
//                .setName("指标当前值")
//                .setItems(list2);
//        subtitles.add(subtitle2);
//
//        //一条
//        List<String> list5 = new ArrayList<>();
//        list5.add("小时提交量 < 100条（昨日同时段提交量）\\* 90%");
//        list5.add("小时提交量 < 100条（上周同时段提交量）\\* 90%");
//        list5.add("小时提交量 < 1条（固定值）");
//        Subtitle subtitle5 = new Subtitle()
//                .setBold(true)
//                .setName("报警规则")
//                .setItems(list5);
//        subtitles.add(subtitle5);
//
//        //一条
//        List<String> list4 = new ArrayList<>();
//        list4.add("账号：");
//        list4.add("手机号：");
//        list4.add("内容：");
//        Subtitle subtitle4 = new Subtitle()
//                .setName("0-默认")
//                .setItems(list4);
//        subtitles.add(subtitle4);
//
//
//        //一条
//        List<String> list6 = new ArrayList<>();
//        list6.add("小时提交量 < 100条（昨日同时段提交量）\\* 90%");
//        list6.add("小时提交量 < 100条（上周同时段提交量）\\* 90%");
//        list6.add("小时提交量 < 1条（固定值）");
//        Subtitle subtitle6 = new Subtitle()
//                .setName("报警规则")
//                .setContent("规则")
//                .setFontColor(Red)
//                .setItems(list6);
//        subtitles.add(subtitle6);
//
//        //一条
//        List<String> list3 = new ArrayList<>();
//        list3.add("小时提交量 < 100条（昨日同时段提交量）\\* 90%");
//        list3.add("小时提交量 < 100条（上周同时段提交量）\\* 90%");
//        list3.add("小时提交量 < 1条（固定值）");
//        Subtitle subtitle3 = new Subtitle()
//                .setBold(true)
//                .setName("报警规则")
//                .setContent("规则")
//                .setFontColor(Red)
//                .setItems(list3);
//        subtitles.add(subtitle3);
//
//        Message message = new Message();
//
//        message.setTitle("【报警】                               账号QPS超速报警")
//                .setTitleColor(Red)
//                .setSubtitles(subtitles);
//
//        cardParams.setApp(BusinessAlarm)
//                .setContactIds(userIds)
//                .setMessage(message);
//
//        feishuNoticeService.sendCardMessage(cardParams);
//
//
//    }
//    @Test
//    public void test() throws IOException, BusinessException {
//
//
//
//
//        String s =
//                " {\"card\":\n" +
//                " {\"elements\":[{\"fields\":[{\"text\":\n" +
//                " {\"content\":\"<font color='red'>&#8226;    </font>**<font color='red'>账号：</font>**<font color='red'>alnmwl</font>\\n      <font color='red'>&#8226;    QPS = 1200 条/秒</font>\\n        <font color='red'>&#8226;    QPS = 1200 条/秒</font>\",\"tag\":\"lark_md\"}}],\"tag\":\"div\"}],\"header\":{\"template\":\"red\",\"title\":{\"content\":\"【报警】                        账号QPS超速报警\",\"tag\":\"plain_text\"}}},\"msg_type\":\"interactive\",\"user_ids\":[\"bc5a6826\"]}";
//        NoticeApp noticeApp = BusinessAlarm;
//        String appId = "";
//        String appSecret = "";
//        for (Application app : applications) {
//            if (app.getNoticeApp().equals(noticeApp)){
//                appId = app.getAppId();
//                appSecret = app.getAppSecret();
//            }else {
//                break;
//            }
//        }
//        //发送消息
//        int code = FeishuUtil.postGroupMsg(s, FeishuUtil.getToken(appId, appSecret));
//        log.info(String.valueOf(code));
//
//    }


}
