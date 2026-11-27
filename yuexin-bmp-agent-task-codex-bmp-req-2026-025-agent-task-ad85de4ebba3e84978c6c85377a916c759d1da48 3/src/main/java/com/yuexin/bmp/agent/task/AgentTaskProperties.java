package com.yuexin.bmp.agent.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "agent.task")
public class AgentTaskProperties {
    private java.util.Map<String,String> principalSecrets = new java.util.HashMap<>();
    public java.util.Map<String,String> getPrincipalSecrets(){return principalSecrets;}
    public void setPrincipalSecrets(java.util.Map<String,String> value){principalSecrets=value;}
    private String hmacSecret = "";
    private Set<String> agentPrincipals = new HashSet<>(Collections.singletonList("customer-billing-agent"));
    private Set<String> adminPrincipals = new HashSet<>(Collections.singletonList("bmp-admin"));
    private long timestampWindowSeconds = 300;
    private String mailMode = "noop";

    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
    public Set<String> getAgentPrincipals() { return agentPrincipals; }
    public void setAgentPrincipals(Set<String> agentPrincipals) { this.agentPrincipals = agentPrincipals; }
    public Set<String> getAdminPrincipals() { return adminPrincipals; }
    public void setAdminPrincipals(Set<String> adminPrincipals) { this.adminPrincipals = adminPrincipals; }
    public long getTimestampWindowSeconds() { return timestampWindowSeconds; }
    public void setTimestampWindowSeconds(long timestampWindowSeconds) { this.timestampWindowSeconds = timestampWindowSeconds; }
    public String getMailMode() { return mailMode; }
    public void setMailMode(String mailMode) { this.mailMode = mailMode; }
}
