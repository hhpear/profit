package com.yuexin.bmp.agent.runtime;

import com.yuexin.bmp.agent.runtime.client.AgentTaskClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
class AgentTaskClientTest {
    @Autowired private AgentTaskClient client;
    @Autowired private org.springframework.web.client.RestTemplate restTemplate;
    private MockRestServiceServer server;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void claimsTasksWithHmacHeaders() {
        server.expect(requestTo("http://localhost:8080/internal/tasks/claim"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Agent-Key", "customer-billing-agent"))
                .andExpect(header("X-Agent-Signature", notNullValue(String.class)))
                .andRespond(withSuccess("{\"tasks\":[]}", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> tasks = client.claimTasks(1);
        assertTrue(tasks.isEmpty());
        server.verify();
    }

    @Test
    void executesTaskAndReturnsResult() {
        server.expect(requestTo("http://localhost:8080/internal/tasks/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Agent-Key", "customer-billing-agent"))
                .andExpect(header("X-Agent-Signature", notNullValue(String.class)))
                .andRespond(withSuccess("{\"taskId\":\"TASK-1\",\"state\":\"ACCEPTED\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.executeTask("TASK-1", "REQ-1");
        assertEquals("TASK-1", result.get("taskId"));
        assertEquals("ACCEPTED", result.get("state"));
        server.verify();
    }
}
