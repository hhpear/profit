package com.yuexin.bmp.agent.task.service;

import com.yuexin.bmp.agent.task.model.AgentTask;
import com.yuexin.bmp.agent.task.model.MailOutcome;
import com.yuexin.bmp.agent.task.model.StatementCandidate;

public interface MailClient {
    MailOutcome send(AgentTask task, StatementCandidate candidate);
}
