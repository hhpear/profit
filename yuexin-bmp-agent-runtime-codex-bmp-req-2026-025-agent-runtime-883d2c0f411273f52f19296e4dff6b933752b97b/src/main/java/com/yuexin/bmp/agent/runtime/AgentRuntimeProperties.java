package com.yuexin.bmp.agent.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.runtime")
public class AgentRuntimeProperties {
    private boolean enabled = false;
    private String agentKey = "customer-billing-agent";
    private String runId = "";
    private String taskBaseUrl = "http://localhost:8080";
    private String hmacSecret = "";
    private long pollIntervalMs = 5000;
    private int maxConcurrency = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getTaskBaseUrl() { return taskBaseUrl; }
    public void setTaskBaseUrl(String taskBaseUrl) { this.taskBaseUrl = taskBaseUrl; }
    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
}
