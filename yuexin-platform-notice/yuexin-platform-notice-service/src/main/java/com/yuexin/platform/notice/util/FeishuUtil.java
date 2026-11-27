package com.yuexin.platform.notice.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;


/**
 * @author szy
 * 飞书工具类
 * @create 2023-02- 11:00
 */
@Slf4j
public final class FeishuUtil {

    /**
     * 根据应用app_id和app_secret获得应用的token
     *
     * @param appId     应用app_id
     * @param appSecret 应用app_secret
     * @return 应用的token
     * @throws IOException
     */
    public static String getToken(String appId, String appSecret) {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(mediaType, "");
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal?app_id=" + appId + "&app_secret=" + appSecret)
                .method("POST", body)
                .addHeader("Content-Type", "\"application/json; charset=utf-8\"")
                .build();
        Response response = null;
        JSONObject result = null;
        try {
            response = client.newCall(request).execute();
            String respStr = response.body().string();
            //将响应数据转换json对象
            result = JSONObject.parseObject(respStr);
        } catch (IOException e) {
            log.error("获取飞书Access Token失败", e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        if (result != null) {
            return (String) result.get("tenant_access_token");
        }
        return null;
    }

    /**
     * 群发消息
     *
     * @param msg
     * @param token
     * @throws IOException
     */
    public static void postGroupMsg(String msg, String token) {
        log.info("推送飞书通知:{}", msg);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.Companion.create(msg, mediaType);
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/message/v4/batch_send/")
                .post(body)
                //应用的token
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                log.info("飞书通知返回: {}", response.body().string());
                response.body().close();
            }
        } catch (IOException e) {
            log.error("推送飞书通知失败 \n{}", msg, e);
        }
    }

    public static void sendWebhook(String url, String msg) {
        log.info("推送飞书机器人webhook通知:{}", msg);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.Companion.create(msg, mediaType);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                log.info("飞书通知返回: {}", response.body().string());
                response.body().close();
            }
        } catch (IOException e) {
            log.error("推送飞书通知失败 \n{}", msg, e);
        }
    }

    /**
     * 发送消息
     *
     * @param msg
     * @param token
     * @throws IOException
     */
    public static void sendMsg(String msg, String token) {
        log.info("推送飞书通知:{}", msg);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.Companion.create(msg, mediaType);
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=user_id")
                .post(body)
                //应用的token
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                log.info("飞书通知返回: {}", response.body().string());
                response.body().close();
            }
        } catch (IOException e) {
            log.error("推送飞书通知失败 \n{}", msg, e);
        }
    }

    /**
     * 更新消息
     *
     * @param messageId
     * @param content
     * @param token
     */
    public static void updateMsg(String messageId, String content, String token) {
        log.info("推送飞书通知:{}", content);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.Companion.create(content, mediaType);
        String url = "https://open.feishu.cn/open-apis/im/v1/messages/" + messageId;
        Request request = new Request.Builder()
                .url(url)
                .patch(body)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.body() != null) {
                log.info("飞书通知返回: {}", response.body().string());
                response.body().close();
            }
        } catch (IOException e) {
            log.error("推送飞书通知失败 \n{}", content, e);
        }
    }

}
