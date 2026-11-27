package com.yuexin.bmp.agent.runtime.handler;

import com.yuexin.bmp.agent.runtime.client.AgentTaskClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class StatementMailHandler implements AgentHandler {
    private final AgentTaskClient taskClient;

    public StatementMailHandler(AgentTaskClient taskClient) {
        this.taskClient = taskClient;
    }

    @Override
    public String agentKey() {
        return "customer-billing-agent";
    }

    @Override
    public String bizType() {
        return "statement_mail";
    }

    @Override
    public void handle(Map<String, Object> task) {
        String taskId = String.valueOf(task.get("taskId"));
        taskClient.executeTask(taskId, "REQ-" + UUID.randomUUID());
    }
}
