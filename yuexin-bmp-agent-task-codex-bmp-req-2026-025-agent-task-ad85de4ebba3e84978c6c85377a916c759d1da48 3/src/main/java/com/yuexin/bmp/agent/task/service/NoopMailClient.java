package com.yuexin.bmp.agent.task.service;

import com.yuexin.bmp.agent.task.AgentTaskProperties;
import com.yuexin.bmp.agent.task.model.AgentTask;
import com.yuexin.bmp.agent.task.model.MailOutcome;
import com.yuexin.bmp.agent.task.model.StatementCandidate;
import org.springframework.stereotype.Service;

@Service
public class NoopMailClient implements MailClient {
    private final AgentTaskProperties properties;

    public NoopMailClient(AgentTaskProperties properties) {
        this.properties = properties;
    }

    @Override
    public MailOutcome send(AgentTask task, StatementCandidate candidate) {
        if (!"noop".equals(properties.getMailMode())) {
            throw new IllegalStateException("Only noop mail mode is implemented");
        }
        return MailOutcome.ACCEPTED;
    }
}
