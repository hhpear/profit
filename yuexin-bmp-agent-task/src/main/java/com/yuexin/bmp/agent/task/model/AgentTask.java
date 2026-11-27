package com.yuexin.bmp.agent.task.model;

import java.util.Date;

public class AgentTask {
    private Long id;
    private String taskId;
    private String runId;
    private String processType;
    private Long processRefId;
    private String agentKey;
    private String bizType;
    private Long bizRefId;
    private String state;
    private String requestId;
    private String claimToken;
    private String snapshotJson;
    private String result;
    private String failReason;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public AgentTask setId(Long id) { this.id = id; return this; }
    public String getTaskId() { return taskId; }
    public AgentTask setTaskId(String taskId) { this.taskId = taskId; return this; }
    public String getRunId() { return runId; }
    public AgentTask setRunId(String runId) { this.runId = runId; return this; }
    public String getProcessType() { return processType; }
    public AgentTask setProcessType(String processType) { this.processType = processType; return this; }
    public Long getProcessRefId() { return processRefId; }
    public AgentTask setProcessRefId(Long processRefId) { this.processRefId = processRefId; return this; }
    public String getAgentKey() { return agentKey; }
    public AgentTask setAgentKey(String agentKey) { this.agentKey = agentKey; return this; }
    public String getBizType() { return bizType; }
    public AgentTask setBizType(String bizType) { this.bizType = bizType; return this; }
    public Long getBizRefId() { return bizRefId; }
    public AgentTask setBizRefId(Long bizRefId) { this.bizRefId = bizRefId; return this; }
    public String getState() { return state; }
    public AgentTask setState(String state) { this.state = state; return this; }
    public String getRequestId() { return requestId; }
    public AgentTask setRequestId(String requestId) { this.requestId = requestId; return this; }
    public String getClaimToken() { return claimToken; }
    public AgentTask setClaimToken(String claimToken) { this.claimToken = claimToken; return this; }
    public String getSnapshotJson() { return snapshotJson; }
    public AgentTask setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; return this; }
    public String getResult() { return result; }
    public AgentTask setResult(String result) { this.result = result; return this; }
    public String getFailReason() { return failReason; }
    public AgentTask setFailReason(String failReason) { this.failReason = failReason; return this; }
    public Date getCreatedAt() { return createdAt; }
    public AgentTask setCreatedAt(Date createdAt) { this.createdAt = createdAt; return this; }
    public Date getUpdatedAt() { return updatedAt; }
    public AgentTask setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; return this; }
}
