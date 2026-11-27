package com.yuexin.platform.notice.service.impl;

import com.alibaba.fastjson.JSON;
import com.yuexin.BusinessException;
import com.yuexin.platform.notice.config.AppConfig;
import com.yuexin.platform.notice.service.ScreenNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author szy
 * @create 2023-04-12 13:58
 */
@DubboService
@Component
@Slf4j
@RequiredArgsConstructor
public class ScreenNoticeServiceImpl implements ScreenNoticeService {

    private final AppConfig appConfig;

    @Override
    public void sendScreenMessage(MessageParams params) throws BusinessException, IOException {
        //将参数转换为json形式
        String msg = JSON.toJSONString(params);

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(mediaType, msg);
        Request request = new Request.Builder()
                .url(appConfig.getScreenNoticeUrl() + "/pub/alarms/biz?platform=" + params.getPlatform())
                .method("POST", body)
                .addHeader("Content-Type", "text/plain")
                .build();
        Response response = client.newCall(request).execute();
        response.close();
    }

}
