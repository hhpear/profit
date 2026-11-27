package com.yuexin.bmp.agent.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentTaskProperties.class)
public class AgentTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentTaskApplication.class, args);
    }
}
