package com.yuexin.bmp.agent.task.model;

import java.math.BigDecimal;

public class StatementCandidate {
    private Long id;
    private Long pid;
    private String platform;
    private String entCode;
    private String month;
    private String state;
    private BigDecimal chargedMoney;
    private String email;
    private Boolean manual;
    private Long billJobId;
    private String billJobState;

    public Long getId() { return id; }
    public StatementCandidate setId(Long id) { this.id = id; return this; }
    public Long getPid() { return pid; }
    public StatementCandidate setPid(Long pid) { this.pid = pid; return this; }
    public String getPlatform() { return platform; }
    public StatementCandidate setPlatform(String platform) { this.platform = platform; return this; }
    public String getEntCode() { return entCode; }
    public StatementCandidate setEntCode(String entCode) { this.entCode = entCode; return this; }
    public String getMonth() { return month; }
    public StatementCandidate setMonth(String month) { this.month = month; return this; }
    public String getState() { return state; }
    public StatementCandidate setState(String state) { this.state = state; return this; }
    public BigDecimal getChargedMoney() { return chargedMoney; }
    public StatementCandidate setChargedMoney(BigDecimal chargedMoney) { this.chargedMoney = chargedMoney; return this; }
    public String getEmail() { return email; }
    public StatementCandidate setEmail(String email) { this.email = email; return this; }
    public Boolean getManual() { return manual; }
    public StatementCandidate setManual(Boolean manual) { this.manual = manual; return this; }
    public Long getBillJobId() { return billJobId; }
    public StatementCandidate setBillJobId(Long billJobId) { this.billJobId = billJobId; return this; }
    public String getBillJobState() { return billJobState; }
    public StatementCandidate setBillJobState(String billJobState) { this.billJobState = billJobState; return this; }
    public Long processRefId() { return pid == null ? id : pid; }
}
