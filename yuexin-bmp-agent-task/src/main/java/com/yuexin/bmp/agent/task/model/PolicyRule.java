package com.yuexin.bmp.agent.task.model;

import java.util.ArrayList;
import java.util.List;

public class PolicyRule {
    private String billMonth;
    private List<CustomerRef> whitelist = new ArrayList<>();
    private List<CustomerRef> excludedCustomers = new ArrayList<>();
    private String sendWindowStart = "09:00";
    private String sendWindowEnd = "18:00";
    private int maxTasksPerRun = 5;

    public String getBillMonth() { return billMonth; }
    public PolicyRule setBillMonth(String billMonth) { this.billMonth = billMonth; return this; }
    public List<CustomerRef> getWhitelist() { return whitelist; }
    public PolicyRule setWhitelist(List<CustomerRef> whitelist) { this.whitelist = whitelist; return this; }
    public List<CustomerRef> getExcludedCustomers() { return excludedCustomers; }
    public PolicyRule setExcludedCustomers(List<CustomerRef> excludedCustomers) { this.excludedCustomers = excludedCustomers; return this; }
    public String getSendWindowStart() { return sendWindowStart; }
    public PolicyRule setSendWindowStart(String sendWindowStart) { this.sendWindowStart = sendWindowStart; return this; }
    public String getSendWindowEnd() { return sendWindowEnd; }
    public PolicyRule setSendWindowEnd(String sendWindowEnd) { this.sendWindowEnd = sendWindowEnd; return this; }
    public int getMaxTasksPerRun() { return maxTasksPerRun; }
    public PolicyRule setMaxTasksPerRun(int maxTasksPerRun) { this.maxTasksPerRun = maxTasksPerRun; return this; }
}
