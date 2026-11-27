package com.yuexin.bmp.agent.task;

import com.yuexin.bmp.agent.task.model.*;
import com.yuexin.bmp.agent.task.repo.AgentTaskRepository;
import com.yuexin.bmp.agent.task.service.MailClient;
import com.yuexin.bmp.agent.task.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TaskExecutionServiceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentTaskRepository repository;
    @Autowired private TaskExecutionService service;
    @MockBean private MailClient mailClient;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM agent_task_event");
        jdbc.update("DELETE FROM agent_task");
        jdbc.update("DELETE FROM agent_run");
        jdbc.update("DELETE FROM agent_policy");
    }

    @Test
    void executesTaskAndWritesEvents() {
        Mockito.when(mailClient.send(Mockito.any(), Mockito.any())).thenReturn(MailOutcome.ACCEPTED);
        Date now = new Date();
        repository.insertPolicy(new AgentPolicy().setPolicyNo("P").setVersion(1).setStatus("ENABLED")
                .setProcessType("customer_statement_process").setAgentKey("customer-billing-agent")
                .setBizType("statement_mail").setBillMonth("2026-11").setRuleJson("{}")
                .setApprovedBy("a").setApprovedAt(now).setCreatedBy("c").setCreatedAt(now).setUpdatedAt(now));
        repository.insertRun(new AgentRun().setRunId("RUN-1").setPolicyId(1L).setPolicyVersion(1)
                .setPolicySnapshot("{}").setProcessType("customer_statement_process").setAgentKey("customer-billing-agent")
                .setBizType("statement_mail").setBillMonth("2026-11").setStatus("RUNNING").setGrantStatus("ACTIVE")
                .setGrantExpiresAt(now).setMaxTasks(1).setCreatedAt(now));
        repository.insertTask(new AgentTask().setTaskId("TASK-1").setRunId("RUN-1")
                .setProcessType("customer_statement_process").setProcessRefId(100L).setAgentKey("customer-billing-agent")
                .setBizType("statement_mail").setBizRefId(101L).setState("READY").setSnapshotJson("{}")
                .setCreatedAt(now).setUpdatedAt(now));

        AgentTask result = service.execute("TASK-1", "REQ-1", "customer-billing-agent");
        assertEquals("ACCEPTED", result.getState());
        assertEquals("ACCEPTED", result.getResult());
        assertEquals(2, repository.findEventsByProcessRef(100L).size());
    }

    @Test
    void secondExecutionDoesNotDuplicateTask() {
        Mockito.when(mailClient.send(Mockito.any(), Mockito.any())).thenReturn(MailOutcome.ACCEPTED);
        Date now = new Date();
        repository.insertRun(new AgentRun().setRunId("RUN-2").setPolicyId(1L).setPolicyVersion(1)
                .setPolicySnapshot("{}").setProcessType("customer_statement_process").setAgentKey("customer-billing-agent")
                .setBizType("statement_mail").setBillMonth("2026-11").setStatus("RUNNING").setGrantStatus("ACTIVE")
                .setGrantExpiresAt(now).setMaxTasks(1).setCreatedAt(now));
        repository.insertTask(new AgentTask().setTaskId("TASK-2").setRunId("RUN-2")
                .setProcessType("customer_statement_process").setProcessRefId(200L).setAgentKey("customer-billing-agent")
                .setBizType("statement_mail").setBizRefId(201L).setState("READY").setSnapshotJson("{}")
                .setCreatedAt(now).setUpdatedAt(now));

        AgentTask first = service.execute("TASK-2", "REQ-1", "customer-billing-agent");
        AgentTask second = service.execute("TASK-2", "REQ-2", "customer-billing-agent");
        assertEquals("ACCEPTED", first.getState());
        assertEquals("ACCEPTED", second.getState());
        assertEquals(2, repository.findEventsByProcessRef(200L).size());
    }
}
