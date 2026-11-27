package com.yuexin.bmp.agent.task.model;

import java.util.Date;

public class AgentRun {
    private Long id;
    private String runId;
    private Long policyId;
    private int policyVersion;
    private String policySnapshot;
    private String processType;
    private String agentKey;
    private String bizType;
    private String billMonth;
    private String status;
    private String grantStatus;
    private Date grantExpiresAt;
    private int maxTasks;
    private Date createdAt;

    public Long getId() { return id; }
    public AgentRun setId(Long id) { this.id = id; return this; }
    public String getRunId() { return runId; }
    public AgentRun setRunId(String runId) { this.runId = runId; return this; }
    public Long getPolicyId() { return policyId; }
    public AgentRun setPolicyId(Long policyId) { this.policyId = policyId; return this; }
    public int getPolicyVersion() { return policyVersion; }
    public AgentRun setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; return this; }
    public String getPolicySnapshot() { return policySnapshot; }
    public AgentRun setPolicySnapshot(String policySnapshot) { this.policySnapshot = policySnapshot; return this; }
    public String getProcessType() { return processType; }
    public AgentRun setProcessType(String processType) { this.processType = processType; return this; }
    public String getAgentKey() { return agentKey; }
    public AgentRun setAgentKey(String agentKey) { this.agentKey = agentKey; return this; }
    public String getBizType() { return bizType; }
    public AgentRun setBizType(String bizType) { this.bizType = bizType; return this; }
    public String getBillMonth() { return billMonth; }
    public AgentRun setBillMonth(String billMonth) { this.billMonth = billMonth; return this; }
    public String getStatus() { return status; }
    public AgentRun setStatus(String status) { this.status = status; return this; }
    public String getGrantStatus() { return grantStatus; }
    public AgentRun setGrantStatus(String grantStatus) { this.grantStatus = grantStatus; return this; }
    public Date getGrantExpiresAt() { return grantExpiresAt; }
    public AgentRun setGrantExpiresAt(Date grantExpiresAt) { this.grantExpiresAt = grantExpiresAt; return this; }
    public int getMaxTasks() { return maxTasks; }
    public AgentRun setMaxTasks(int maxTasks) { this.maxTasks = maxTasks; return this; }
    public Date getCreatedAt() { return createdAt; }
    public AgentRun setCreatedAt(Date createdAt) { this.createdAt = createdAt; return this; }
}
