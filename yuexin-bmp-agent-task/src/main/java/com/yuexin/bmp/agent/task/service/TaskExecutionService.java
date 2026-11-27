package com.yuexin.bmp.agent.task.service;

import com.yuexin.bmp.agent.task.model.*;
import com.yuexin.bmp.agent.task.repo.AgentTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TaskExecutionService {
    private final AgentTaskRepository repository;
    private final MailClient mailClient;

    public TaskExecutionService(AgentTaskRepository repository, MailClient mailClient) {
        this.repository = repository;
        this.mailClient = mailClient;
    }

    public List<AgentTask> claim(String runId, String agentKey, int limit) {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        AgentRun run = repository.findRunByRunId(runId).orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!"ACTIVE".equals(run.getGrantStatus()) || !"RUNNING".equals(run.getStatus())) {
            return Collections.emptyList();
        }
        if (!Objects.equals(run.getAgentKey(), agentKey)) {
            throw new IllegalArgumentException("agent key does not match run");
        }
        return repository.findReadyTasks(runId, limit);
    }

    @Transactional
    public AgentTask execute(String taskId, String requestId, String agentKey) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new IllegalArgumentException("requestId is required");
        }
        AgentTask task = repository.findTaskByTaskId(taskId).orElseThrow(() -> new IllegalArgumentException("task not found"));
        if (!Objects.equals(task.getAgentKey(), agentKey)) {
            throw new IllegalArgumentException("agent key does not match task");
        }
        if (!"READY".equals(task.getState())) {
            return task;
        }
        AgentRun run = repository.findRunByRunId(task.getRunId()).orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!"ACTIVE".equals(run.getGrantStatus())) {
            repository.insertEvent(event(task, "AUTH_DENIED", "SYSTEM", agentKey, null, "{\"reason\":\"grant is not active\"}"));
            return task;
        }
        if (repository.claimTask(taskId, requestId, UUID.randomUUID().toString()) != 1) {
            return repository.findTaskByTaskId(taskId).orElse(task);
        }
        repository.insertEvent(event(task, "TASK_CLAIMED", "AGENT", agentKey, null, "{\"requestId\":\"" + requestId + "\"}"));

        AgentTask claimed = repository.findTaskByTaskId(taskId).orElseThrow(() -> new IllegalStateException("task disappeared"));
        StatementCandidate candidate = candidateFromSnapshot(claimed);
        MailOutcome outcome;
        try {
            outcome = mailClient.send(claimed, candidate);
        } catch (Exception e) {
            outcome = MailOutcome.UNKNOWN;
        }
        String state = outcome == MailOutcome.ACCEPTED ? "ACCEPTED" : outcome == MailOutcome.FAILED ? "FAILED" : "UNKNOWN";
        repository.completeTask(taskId, state, outcome, outcome == MailOutcome.ACCEPTED ? null : outcome.name());
        repository.insertEvent(event(claimed, "SMTP_" + outcome.name(), "SYSTEM", agentKey, null, "{\"requestId\":\"" + requestId + "\"}"));
        return repository.findTaskByTaskId(taskId).orElse(claimed);
    }

    private StatementCandidate candidateFromSnapshot(AgentTask task) {
        return new StatementCandidate().setId(task.getBizRefId());
    }

    private AgentTaskEvent event(AgentTask task, String type, String actorType, String actorId, String decisionId, String detail) {
        return new AgentTaskEvent()
                .setEventId("EVENT-" + UUID.randomUUID())
                .setTaskId(task.getTaskId())
                .setRunId(task.getRunId())
                .setProcessRefId(task.getProcessRefId())
                .setAgentKey(task.getAgentKey())
                .setEventType(type)
                .setActorType(actorType)
                .setActorId(actorId)
                .setDecisionId(decisionId)
                .setDetailJson(detail)
                .setCreatedAt(new Date());
    }
}
