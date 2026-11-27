package com.yuexin.bmp.agent.task.model;

import java.util.Date;

public class AgentPolicy {
    private Long id;
    private String policyNo;
    private int version;
    private String status;
    private String processType;
    private String agentKey;
    private String bizType;
    private String billMonth;
    private String ruleJson;
    private String approvedBy;
    private Date approvedAt;
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public AgentPolicy setId(Long id) { this.id = id; return this; }
    public String getPolicyNo() { return policyNo; }
    public AgentPolicy setPolicyNo(String policyNo) { this.policyNo = policyNo; return this; }
    public int getVersion() { return version; }
    public AgentPolicy setVersion(int version) { this.version = version; return this; }
    public String getStatus() { return status; }
    public AgentPolicy setStatus(String status) { this.status = status; return this; }
    public String getProcessType() { return processType; }
    public AgentPolicy setProcessType(String processType) { this.processType = processType; return this; }
    public String getAgentKey() { return agentKey; }
    public AgentPolicy setAgentKey(String agentKey) { this.agentKey = agentKey; return this; }
    public String getBizType() { return bizType; }
    public AgentPolicy setBizType(String bizType) { this.bizType = bizType; return this; }
    public String getBillMonth() { return billMonth; }
    public AgentPolicy setBillMonth(String billMonth) { this.billMonth = billMonth; return this; }
    public String getRuleJson() { return ruleJson; }
    public AgentPolicy setRuleJson(String ruleJson) { this.ruleJson = ruleJson; return this; }
    public String getApprovedBy() { return approvedBy; }
    public AgentPolicy setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; return this; }
    public Date getApprovedAt() { return approvedAt; }
    public AgentPolicy setApprovedAt(Date approvedAt) { this.approvedAt = approvedAt; return this; }
    public String getCreatedBy() { return createdBy; }
    public AgentPolicy setCreatedBy(String createdBy) { this.createdBy = createdBy; return this; }
    public Date getCreatedAt() { return createdAt; }
    public AgentPolicy setCreatedAt(Date createdAt) { this.createdAt = createdAt; return this; }
    public Date getUpdatedAt() { return updatedAt; }
    public AgentPolicy setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; return this; }
}
