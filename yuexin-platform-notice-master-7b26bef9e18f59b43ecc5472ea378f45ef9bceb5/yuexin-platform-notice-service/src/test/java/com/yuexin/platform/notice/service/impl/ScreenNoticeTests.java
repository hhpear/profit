package com.yuexin.platform.notice.service.impl;

import com.yuexin.BusinessException;
import com.yuexin.platform.notice.model.AlarmLevel;
import com.yuexin.platform.notice.service.ScreenNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

/**
 * @author szy
 * @create 2023-04-12 14:15
 */
@DisplayName("发送监控大屏消息")
@SpringBootTest
@ExtendWith(SpringExtension.class)
@Slf4j
public class ScreenNoticeTests {

    @Autowired
    private ScreenNoticeService screenNoticeService;

    @Test
    @DisplayName("发消息")
    public void testPost() throws IOException, BusinessException{
        ScreenNoticeService.MessageParams messageParams = new ScreenNoticeService.MessageParams()
                .setPlatform("YueXinYun")
                .setAlarmId("1253")
                .setName("CPU超限报警")
                .setInstanceName("测试实例")
                .setMetricName("测试监控项名称")
                .setAlarmValue("1253")
                .setAlarmTime(1338388833L)
                .setDuration(500L)
                .setAlarmLevel(AlarmLevel.Normal);


        screenNoticeService.sendScreenMessage(messageParams);
    }
}
