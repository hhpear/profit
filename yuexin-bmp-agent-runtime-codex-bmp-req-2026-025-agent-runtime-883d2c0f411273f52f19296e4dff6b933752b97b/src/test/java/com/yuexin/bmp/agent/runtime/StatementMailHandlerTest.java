package com.yuexin.bmp.agent.runtime;

import com.yuexin.bmp.agent.runtime.client.AgentTaskClient;
import com.yuexin.bmp.agent.runtime.handler.StatementMailHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatementMailHandlerTest {
    @Test
    void executesTaskThroughTaskClient() {
        AgentTaskClient client = Mockito.mock(AgentTaskClient.class);
        StatementMailHandler handler = new StatementMailHandler(client);
        Map<String, Object> task = new HashMap<>();
        task.put("taskId", "TASK-1");
        task.put("agentKey", "customer-billing-agent");
        task.put("bizType", "statement_mail");

        handler.handle(task);

        assertEquals("customer-billing-agent", handler.agentKey());
        assertEquals("statement_mail", handler.bizType());
        Mockito.verify(client).executeTask(Mockito.eq("TASK-1"), Mockito.startsWith("REQ-"));
    }
}
