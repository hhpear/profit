package com.yuexin.bmp.agent.task.model;

import java.util.Date;

public class AgentTaskEvent {
    private Long id;
    private String eventId;
    private String taskId;
    private String runId;
    private Long processRefId;
    private String agentKey;
    private String eventType;
    private String actorType;
    private String actorId;
    private String decisionId;
    private String detailJson;
    private Date createdAt;

    public Long getId() { return id; }
    public AgentTaskEvent setId(Long id) { this.id = id; return this; }
    public String getEventId() { return eventId; }
    public AgentTaskEvent setEventId(String eventId) { this.eventId = eventId; return this; }
    public String getTaskId() { return taskId; }
    public AgentTaskEvent setTaskId(String taskId) { this.taskId = taskId; return this; }
    public String getRunId() { return runId; }
    public AgentTaskEvent setRunId(String runId) { this.runId = runId; return this; }
    public Long getProcessRefId() { return processRefId; }
    public AgentTaskEvent setProcessRefId(Long processRefId) { this.processRefId = processRefId; return this; }
    public String getAgentKey() { return agentKey; }
    public AgentTaskEvent setAgentKey(String agentKey) { this.agentKey = agentKey; return this; }
    public String getEventType() { return eventType; }
    public AgentTaskEvent setEventType(String eventType) { this.eventType = eventType; return this; }
    public String getActorType() { return actorType; }
    public AgentTaskEvent setActorType(String actorType) { this.actorType = actorType; return this; }
    public String getActorId() { return actorId; }
    public AgentTaskEvent setActorId(String actorId) { this.actorId = actorId; return this; }
    public String getDecisionId() { return decisionId; }
    public AgentTaskEvent setDecisionId(String decisionId) { this.decisionId = decisionId; return this; }
    public String getDetailJson() { return detailJson; }
    public AgentTaskEvent setDetailJson(String detailJson) { this.detailJson = detailJson; return this; }
    public Date getCreatedAt() { return createdAt; }
    public AgentTaskEvent setCreatedAt(Date createdAt) { this.createdAt = createdAt; return this; }
}
