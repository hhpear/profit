package com.yuexin.bmp.agent.task;

import com.yuexin.bmp.agent.task.model.*;
import com.yuexin.bmp.agent.task.repo.AgentTaskRepository;
import com.yuexin.bmp.agent.task.service.TaskGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TaskGenerationServiceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentTaskRepository repository;
    @Autowired private TaskGenerationService service;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM agent_task_event");
        jdbc.update("DELETE FROM agent_task");
        jdbc.update("DELETE FROM agent_run");
        jdbc.update("DELETE FROM agent_policy");
        jdbc.update("DROP TABLE IF EXISTS customer_statement");
        jdbc.update("DROP TABLE IF EXISTS customer_bill_info");
        jdbc.update("DROP TABLE IF EXISTS customer_bill_job");
        jdbc.update("CREATE TABLE customer_statement(id BIGINT PRIMARY KEY,pid BIGINT,platform VARCHAR(32),ent_code VARCHAR(32),`month` DATE,state VARCHAR(32),charged_money DECIMAL(18,2),bill_job_id BIGINT)");
        jdbc.update("CREATE TABLE customer_bill_info(platform VARCHAR(32),ent_code VARCHAR(32),email VARCHAR(256),manual BOOLEAN,bill_send VARCHAR(32))");
        jdbc.update("CREATE TABLE customer_bill_job(id BIGINT PRIMARY KEY,platform VARCHAR(32),ent_code VARCHAR(32),`month` DATE,state VARCHAR(32),deleted BOOLEAN)");
    }

    @Test
    void generatesTaskAndUsesParentStatementAsProcessRoot() {
        AgentPolicy policy = policy("{\"billMonth\":\"2026-11\",\"whitelist\":[{\"platform\":\"SH\",\"entCode\":\"E1\"}],\"excludedCustomers\":[],\"maxTasksPerRun\":5}");
        statement(100L, null, "E1", null, null);
        billInfo("E1", "a@example.com", false);

        AgentRun run = service.generate(policy.getId(), "tester");
        List<AgentTask> tasks = repository.findReadyTasks(run.getRunId(), 10);
        assertEquals(1, tasks.size());
        assertEquals(100L, tasks.get(0).getProcessRefId());
        assertEquals(100L, tasks.get(0).getBizRefId());
        assertEquals("customer-billing-agent", tasks.get(0).getAgentKey());
        assertEquals("statement_mail", tasks.get(0).getBizType());
        assertEquals(1, repository.findEventsByProcessRef(100L).size());
    }

    @Test
    void splitChildUsesParentPidAsProcessRoot() {
        AgentPolicy policy = policy("{\"billMonth\":\"2026-11\",\"whitelist\":[{\"platform\":\"SH\",\"entCode\":\"E1\"}],\"excludedCustomers\":[],\"maxTasksPerRun\":5}");
        statement(101L, 100L, "E1", null, null);
        billInfo("E1", "a@example.com", false);

        AgentRun run = service.generate(policy.getId(), "tester");
        AgentTask task = repository.findReadyTasks(run.getRunId(), 10).get(0);
        assertEquals(100L, task.getProcessRefId());
        assertEquals(101L, task.getBizRefId());
    }

    @Test
    void excludesBlacklistedCustomerAndIncompleteManualBill() {
        AgentPolicy policy = policy("{\"billMonth\":\"2026-11\",\"whitelist\":[{\"platform\":\"SH\",\"entCode\":\"E1\"},{\"platform\":\"SH\",\"entCode\":\"E2\"}],\"excludedCustomers\":[{\"platform\":\"SH\",\"entCode\":\"E1\"}],\"maxTasksPerRun\":5}");
        statement(100L, null, "E1", null, null);
        statement(101L, null, "E2", null, null);
        billInfo("E1", "a@example.com", false);
        billInfo("E2", "b@example.com", true);

        AgentRun run = service.generate(policy.getId(), "tester");
        assertTrue(repository.findReadyTasks(run.getRunId(), 10).isEmpty());
    }

    @Test
    void generatesManualBillWhenBillJobCompleted() {
        AgentPolicy policy = policy("{\"billMonth\":\"2026-11\",\"whitelist\":[{\"platform\":\"SH\",\"entCode\":\"E2\"}],\"excludedCustomers\":[],\"maxTasksPerRun\":5}");
        jdbc.update("INSERT INTO customer_bill_job(id,platform,ent_code,`month`,state,deleted) VALUES(900,'SH','E2','2026-11-01','Completed',false)");
        statement(101L, null, "E2", 900L, "Completed");
        billInfo("E2", "b@example.com", true);

        AgentRun run = service.generate(policy.getId(), "tester");
        assertEquals(1, repository.findReadyTasks(run.getRunId(), 10).size());
    }

    @Test
    void repeatedGenerationIsIdempotent() {
        AgentPolicy policy = policy("{\"billMonth\":\"2026-11\",\"whitelist\":[{\"platform\":\"SH\",\"entCode\":\"E1\"}],\"excludedCustomers\":[],\"maxTasksPerRun\":5}");
        statement(100L, null, "E1", null, null);
        billInfo("E1", "a@example.com", false);

        AgentRun first = service.generate(policy.getId(), "tester");
        AgentRun second = service.generate(policy.getId(), "tester");
        assertEquals(first.getRunId(), second.getRunId());
        assertEquals(1, repository.findReadyTasks(first.getRunId(), 10).size());
    }

    private void statement(Long id, Long pid, String entCode, Long billJobId, String billJobState) {
        jdbc.update("INSERT INTO customer_statement(id,pid,platform,ent_code,`month`,state,charged_money,bill_job_id) VALUES(?,?, 'SH',?,'2026-11-01','Generated',10.00,?)", id, pid, entCode, billJobId);
    }

    private void billInfo(String entCode, String email, boolean manual) {
        jdbc.update("INSERT INTO customer_bill_info(platform,ent_code,email,manual,bill_send) VALUES('SH',?,?,?,'Auto')", entCode, email, manual);
    }

    private AgentPolicy policy(String rule) {
        Date now = new Date();
        return repository.insertPolicy(new AgentPolicy()
                .setPolicyNo("MAIL-2026-11")
                .setVersion(1)
                .setStatus("ENABLED")
                .setProcessType("customer_statement_process")
                .setAgentKey("customer-billing-agent")
                .setBizType("statement_mail")
                .setBillMonth("2026-11")
                .setRuleJson(rule)
                .setApprovedBy("approver")
                .setApprovedAt(now)
                .setCreatedBy("creator")
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }
}
