package com.yuexin.bmp.agent.runtime.handler;

import com.yuexin.bmp.agent.runtime.AgentRuntimeProperties;
import com.yuexin.bmp.agent.runtime.client.AgentTaskClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RuntimeScheduler {
    private final AgentTaskClient client;
    private final List<AgentHandler> handlers;
    private final AgentRuntimeProperties properties;

    public RuntimeScheduler(AgentTaskClient client, List<AgentHandler> handlers, AgentRuntimeProperties properties) {
        this.client = client;
        this.handlers = handlers;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${agent.runtime.poll-interval-ms:5000}")
    public void poll() {
        if (!properties.isEnabled() || properties.getRunId() == null || properties.getRunId().trim().isEmpty()) {
            return;
        }
        List<Map<String, Object>> tasks = client.claimTasks(properties.getMaxConcurrency());
        for (Map<String, Object> task : tasks) {
            String agentKey = String.valueOf(task.get("agentKey"));
            String bizType = String.valueOf(task.get("bizType"));
            for (AgentHandler handler : handlers) {
                if (handler.agentKey().equals(agentKey) && handler.bizType().equals(bizType)) {
                    handler.handle(task);
                    break;
                }
            }
        }
    }
}
