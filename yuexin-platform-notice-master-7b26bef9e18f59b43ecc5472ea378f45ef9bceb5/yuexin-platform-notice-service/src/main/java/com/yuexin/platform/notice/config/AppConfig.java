package com.yuexin.platform.notice.config;

import com.yuexin.BusinessException;
import com.yuexin.platform.notice.param.Application;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author jiachun
 * @date 2023/5/12
 */
@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "app-config")
public class AppConfig implements InitializingBean {

    private List<Application> feishuApps;

    private int feishuRateLimit = 20;

    private String screenNoticeUrl = "http://yuexin-nchan-gateway:8303";

    @Override
    public void afterPropertiesSet() throws Exception {
        if (feishuApps == null || feishuApps.isEmpty()) {
            throw new BusinessException("未配置飞书通知应用!");
        }
    }
}
