package com.yuexin.bmp.agent.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuexin.bmp.agent.task.model.*;
import com.yuexin.bmp.agent.task.repo.AgentTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TaskGenerationService {
    private final AgentTaskRepository repository;
    private final ObjectMapper objectMapper;

    public TaskGenerationService(AgentTaskRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentRun generate(Long policyId, String actor) {
        AgentPolicy policy = repository.findPolicyById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("policy not found"));
        if (!"ENABLED".equals(policy.getStatus())) {
            throw new IllegalStateException("policy is not enabled");
        }
        PolicyRule rule = parseRule(policy);
        if (!Objects.equals(policy.getBillMonth(), rule.getBillMonth())) {
            throw new IllegalStateException("policy bill month does not match rule");
        }
        if (rule.getMaxTasksPerRun() <= 0) {
            throw new IllegalStateException("maxTasksPerRun must be positive");
        }
        Optional<AgentRun> existing = repository.findRunByPolicy(policy.getId(), policy.getBillMonth(), policy.getVersion());
        if (existing.isPresent()) {
            return existing.get();
        }

        Date now = new Date();
        Calendar expiry = Calendar.getInstance();
        expiry.setTime(now);
        expiry.add(Calendar.HOUR, 24);

        AgentRun run = new AgentRun()
                .setRunId("RUN-" + UUID.randomUUID())
                .setPolicyId(policy.getId())
                .setPolicyVersion(policy.getVersion())
                .setPolicySnapshot(policy.getRuleJson())
                .setProcessType(policy.getProcessType())
                .setAgentKey(policy.getAgentKey())
                .setBizType(policy.getBizType())
                .setBillMonth(policy.getBillMonth())
                .setStatus("RUNNING")
                .setGrantStatus("ACTIVE")
                .setGrantExpiresAt(expiry.getTime())
                .setMaxTasks(rule.getMaxTasksPerRun())
                .setCreatedAt(now);
        repository.insertRun(run);

        List<StatementCandidate> candidates = repository.findCandidates(policy.getBillMonth(), rule.getWhitelist(), rule.getExcludedCustomers());
        int created = 0;
        for (StatementCandidate candidate : candidates) {
            if (created >= rule.getMaxTasksPerRun()) {
                break;
            }
            if (isBlocked(candidate)) {
                continue;
            }
            AgentTask task = new AgentTask()
                    .setTaskId("TASK-" + UUID.randomUUID())
                    .setRunId(run.getRunId())
                    .setProcessType(policy.getProcessType())
                    .setProcessRefId(candidate.processRefId())
                    .setAgentKey(policy.getAgentKey())
                    .setBizType(policy.getBizType())
                    .setBizRefId(candidate.getId())
                    .setState("READY")
                    .setSnapshotJson(snapshot(candidate))
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            repository.insertTask(task);
            repository.insertEvent(event(task, "TASK_CREATED", "SYSTEM", actor, null,
                    "{\"readiness\":\"Generated\",\"manual\":" + Boolean.TRUE.equals(candidate.getManual()) + "}"));
            created++;
        }
        repository.insertEvent(new AgentTaskEvent()
                .setEventId("EVENT-" + UUID.randomUUID())
                .setRunId(run.getRunId())
                .setAgentKey(policy.getAgentKey())
                .setEventType("RUN_CREATED")
                .setActorType("HUMAN")
                .setActorId(actor)
                .setDetailJson("{\"candidateCount\":" + candidates.size() + ",\"createdTasks\":" + created + "}")
                .setCreatedAt(now));
        return run;
    }

    private boolean isBlocked(StatementCandidate candidate) {
        if (candidate.getEmail() == null || candidate.getEmail().trim().isEmpty()) {
            return true;
        }
        if (Boolean.TRUE.equals(candidate.getManual())) {
            return candidate.getBillJobId() == null || !"Completed".equals(candidate.getBillJobState());
        }
        return false;
    }

    private String snapshot(StatementCandidate candidate) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("statementId", candidate.getId());
            map.put("processRefId", candidate.processRefId());
            map.put("platform", candidate.getPlatform());
            map.put("entCode", candidate.getEntCode());
            map.put("emailConfigured", candidate.getEmail() != null && !candidate.getEmail().trim().isEmpty());
            map.put("chargedMoney", candidate.getChargedMoney());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("cannot create task snapshot", e);
        }
    }

    private PolicyRule parseRule(AgentPolicy policy) {
        try {
            PolicyRule rule = objectMapper.readValue(policy.getRuleJson(), PolicyRule.class);
            if (rule.getBillMonth() == null || rule.getBillMonth().trim().isEmpty()) {
                throw new IllegalArgumentException("billMonth is required");
            }
            return rule;
        } catch (Exception e) {
            throw new IllegalStateException("invalid policy rule", e);
        }
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
