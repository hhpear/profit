package com.yuexin.bmp.agent.runtime.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuexin.bmp.agent.runtime.AgentRuntimeProperties;
import com.yuexin.bmp.agent.runtime.config.HmacSigner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Component
public class AgentTaskClient {
    private final RestTemplate restTemplate;
    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public AgentTaskClient(RestTemplate restTemplate, AgentRuntimeProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> claimTasks(int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", properties.getRunId());
        body.put("agentKey", properties.getAgentKey());
        body.put("limit", limit);
        Map<String, Object> response = exchange("/internal/tasks/claim", body, Map.class);
        return (List<Map<String, Object>>) response.getOrDefault("tasks", Collections.emptyList());
    }

    public Map<String, Object> executeTask(String taskId, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("requestId", requestId);
        body.put("agentKey", properties.getAgentKey());
        return exchange("/internal/tasks/execute", body, Map.class);
    }

    private <T> T exchange(String path, Object body, Class<T> type) {
        byte[] raw;
        try {
            raw = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize request", e);
        }
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = HmacSigner.sign(HttpMethod.POST.name(), path, timestamp, nonce, raw, properties.getHmacSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Agent-Key", properties.getAgentKey());
        headers.add("X-Agent-Timestamp", timestamp);
        headers.add("X-Agent-Nonce", nonce);
        headers.add("X-Agent-Signature", signature);
        return restTemplate.postForObject(properties.getTaskBaseUrl() + path, new HttpEntity<>(raw, headers), type);
    }
}
