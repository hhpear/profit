package com.yuexin.bmp.agent.task.repo;

import com.yuexin.bmp.agent.task.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class AgentTaskRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public AgentTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public AgentPolicy insertPolicy(AgentPolicy policy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("policyNo", policy.getPolicyNo())
                .addValue("version", policy.getVersion())
                .addValue("status", policy.getStatus())
                .addValue("processType", policy.getProcessType())
                .addValue("agentKey", policy.getAgentKey())
                .addValue("bizType", policy.getBizType())
                .addValue("billMonth", policy.getBillMonth())
                .addValue("ruleJson", policy.getRuleJson())
                .addValue("approvedBy", policy.getApprovedBy())
                .addValue("approvedAt", policy.getApprovedAt())
                .addValue("createdBy", policy.getCreatedBy())
                .addValue("createdAt", policy.getCreatedAt())
                .addValue("updatedAt", policy.getUpdatedAt());
        KeyHolder keys = new GeneratedKeyHolder();
        named.update("INSERT INTO agent_policy(policy_no,version,status,process_type,agent_key,biz_type,bill_month,rule_json,approved_by,approved_at,created_by,created_at,updated_at) VALUES(:policyNo,:version,:status,:processType,:agentKey,:bizType,:billMonth,:ruleJson,:approvedBy,:approvedAt,:createdBy,:createdAt,:updatedAt)", params, keys);
        policy.setId(generatedId(keys));
        return policy;
    }

    public Optional<AgentPolicy> findPolicyById(Long id) {
        return jdbc.query("SELECT * FROM agent_policy WHERE id=?", policyMapper, id).stream().findFirst();
    }

    public Optional<AgentRun> findRunByPolicy(Long policyId, String billMonth, int version) {
        return jdbc.query("SELECT * FROM agent_run WHERE policy_id=? AND bill_month=? AND policy_version=?", runMapper, policyId, billMonth, version).stream().findFirst();
    }

    public AgentRun insertRun(AgentRun run) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runId", run.getRunId())
                .addValue("policyId", run.getPolicyId())
                .addValue("policyVersion", run.getPolicyVersion())
                .addValue("policySnapshot", run.getPolicySnapshot())
                .addValue("processType", run.getProcessType())
                .addValue("agentKey", run.getAgentKey())
                .addValue("bizType", run.getBizType())
                .addValue("billMonth", run.getBillMonth())
                .addValue("status", run.getStatus())
                .addValue("grantStatus", run.getGrantStatus())
                .addValue("grantExpiresAt", run.getGrantExpiresAt())
                .addValue("maxTasks", run.getMaxTasks())
                .addValue("createdAt", run.getCreatedAt());
        KeyHolder keys = new GeneratedKeyHolder();
        named.update("INSERT INTO agent_run(run_id,policy_id,policy_version,policy_snapshot,process_type,agent_key,biz_type,bill_month,status,grant_status,grant_expires_at,max_tasks,created_at) VALUES(:runId,:policyId,:policyVersion,:policySnapshot,:processType,:agentKey,:bizType,:billMonth,:status,:grantStatus,:grantExpiresAt,:maxTasks,:createdAt)", params, keys);
        run.setId(generatedId(keys));
        return run;
    }

    public Optional<AgentRun> findRunByRunId(String runId) {
        return jdbc.query("SELECT * FROM agent_run WHERE run_id=?", runMapper, runId).stream().findFirst();
    }

    public AgentTask insertTask(AgentTask task) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", task.getTaskId())
                .addValue("runId", task.getRunId())
                .addValue("processType", task.getProcessType())
                .addValue("processRefId", task.getProcessRefId())
                .addValue("agentKey", task.getAgentKey())
                .addValue("bizType", task.getBizType())
                .addValue("bizRefId", task.getBizRefId())
                .addValue("state", task.getState())
                .addValue("snapshotJson", task.getSnapshotJson())
                .addValue("createdAt", task.getCreatedAt())
                .addValue("updatedAt", task.getUpdatedAt());
        KeyHolder keys = new GeneratedKeyHolder();
        named.update("INSERT INTO agent_task(task_id,run_id,process_type,process_ref_id,agent_key,biz_type,biz_ref_id,state,snapshot_json,created_at,updated_at) VALUES(:taskId,:runId,:processType,:processRefId,:agentKey,:bizType,:bizRefId,:state,:snapshotJson,:createdAt,:updatedAt)", params, keys);
        task.setId(generatedId(keys));
        return task;
    }

    public Optional<AgentTask> findTaskByTaskId(String taskId) {
        return jdbc.query("SELECT * FROM agent_task WHERE task_id=?", taskMapper, taskId).stream().findFirst();
    }

    public List<AgentTask> findReadyTasks(String runId, int limit) {
        return jdbc.query("SELECT * FROM agent_task WHERE run_id=? AND state='READY' ORDER BY id LIMIT ?", taskMapper, runId, limit);
    }

    public int claimTask(String taskId, String requestId, String claimToken) {
        return jdbc.update("UPDATE agent_task SET state='CLAIMED',request_id=?,claim_token=?,updated_at=? WHERE task_id=? AND state='READY'", requestId, claimToken, new Timestamp(new Date().getTime()), taskId);
    }

    public int completeTask(String taskId, String state, MailOutcome outcome, String failReason) {
        return jdbc.update("UPDATE agent_task SET state=?,result=?,fail_reason=?,updated_at=? WHERE task_id=? AND state='CLAIMED'", state, outcome.name(), failReason, new Timestamp(new Date().getTime()), taskId);
    }

    public AgentTaskEvent insertEvent(AgentTaskEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", event.getEventId())
                .addValue("taskId", event.getTaskId())
                .addValue("runId", event.getRunId())
                .addValue("processRefId", event.getProcessRefId())
                .addValue("agentKey", event.getAgentKey())
                .addValue("eventType", event.getEventType())
                .addValue("actorType", event.getActorType())
                .addValue("actorId", event.getActorId())
                .addValue("decisionId", event.getDecisionId())
                .addValue("detailJson", event.getDetailJson())
                .addValue("createdAt", event.getCreatedAt());
        KeyHolder keys = new GeneratedKeyHolder();
        named.update("INSERT INTO agent_task_event(event_id,task_id,run_id,process_ref_id,agent_key,event_type,actor_type,actor_id,decision_id,detail_json,created_at) VALUES(:eventId,:taskId,:runId,:processRefId,:agentKey,:eventType,:actorType,:actorId,:decisionId,:detailJson,:createdAt)", params, keys);
        event.setId(generatedId(keys));
        return event;
    }

    public List<AgentTaskEvent> findEventsByProcessRef(Long processRefId) {
        return jdbc.query("SELECT * FROM agent_task_event WHERE process_ref_id=? ORDER BY id", eventMapper, processRefId);
    }

    public List<StatementCandidate> findCandidates(String billMonth, List<CustomerRef> whitelist, List<CustomerRef> excluded) {
        StringBuilder sql = new StringBuilder("SELECT s.id,s.pid,s.platform,s.ent_code,s.state,s.charged_money,b.email,b.manual,s.bill_job_id,j.state AS bill_job_state FROM customer_statement s JOIN customer_bill_info b ON b.platform=s.platform AND b.ent_code=s.ent_code LEFT JOIN customer_bill_job j ON j.id=s.bill_job_id WHERE s.state='Generated' AND b.bill_send='Auto' AND YEAR(s.`month`)=:year AND MONTH(s.`month`)=:monthNumber ");
        String[] parts = billMonth.split("-");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("year", Integer.parseInt(parts[0])).addValue("monthNumber", Integer.parseInt(parts[1]));
        if (whitelist != null && !whitelist.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < whitelist.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(s.platform=:wp").append(i).append(" AND s.ent_code=:we").append(i).append(")");
                params.addValue("wp" + i, whitelist.get(i).getPlatform());
                params.addValue("we" + i, whitelist.get(i).getEntCode());
            }
            sql.append(") ");
        }
        if (excluded != null && !excluded.isEmpty()) {
            sql.append(" AND NOT (");
            for (int i = 0; i < excluded.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(s.platform=:ep").append(i).append(" AND s.ent_code=:ee").append(i).append(")");
                params.addValue("ep" + i, excluded.get(i).getPlatform());
                params.addValue("ee" + i, excluded.get(i).getEntCode());
            }
            sql.append(") ");
        }
        sql.append(" ORDER BY s.id");
        return named.query(sql.toString(), params, candidateMapper);
    }

    private Long generatedId(KeyHolder keys) {
        Map<String, Object> values = keys.getKeys();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number) {
                return ((Number) entry.getValue()).longValue();
            }
        }
        throw new IllegalStateException("Generated id not returned");
    }

    private final RowMapper<AgentPolicy> policyMapper = (rs, rowNum) -> new AgentPolicy()
            .setId(rs.getLong("id")).setPolicyNo(rs.getString("policy_no")).setVersion(rs.getInt("version"))
            .setStatus(rs.getString("status")).setProcessType(rs.getString("process_type")).setAgentKey(rs.getString("agent_key"))
            .setBizType(rs.getString("biz_type")).setBillMonth(rs.getString("bill_month")).setRuleJson(rs.getString("rule_json"))
            .setApprovedBy(rs.getString("approved_by")).setApprovedAt(rs.getTimestamp("approved_at"))
            .setCreatedBy(rs.getString("created_by")).setCreatedAt(rs.getTimestamp("created_at")).setUpdatedAt(rs.getTimestamp("updated_at"));

    private final RowMapper<AgentRun> runMapper = (rs, rowNum) -> new AgentRun()
            .setId(rs.getLong("id")).setRunId(rs.getString("run_id")).setPolicyId(rs.getLong("policy_id"))
            .setPolicyVersion(rs.getInt("policy_version")).setPolicySnapshot(rs.getString("policy_snapshot"))
            .setProcessType(rs.getString("process_type")).setAgentKey(rs.getString("agent_key")).setBizType(rs.getString("biz_type"))
            .setBillMonth(rs.getString("bill_month")).setStatus(rs.getString("status")).setGrantStatus(rs.getString("grant_status"))
            .setGrantExpiresAt(rs.getTimestamp("grant_expires_at")).setMaxTasks(rs.getInt("max_tasks")).setCreatedAt(rs.getTimestamp("created_at"));

    private final RowMapper<AgentTask> taskMapper = (rs, rowNum) -> new AgentTask()
            .setId(rs.getLong("id")).setTaskId(rs.getString("task_id")).setRunId(rs.getString("run_id"))
            .setProcessType(rs.getString("process_type")).setProcessRefId(rs.getLong("process_ref_id"))
            .setAgentKey(rs.getString("agent_key")).setBizType(rs.getString("biz_type")).setBizRefId(rs.getLong("biz_ref_id"))
            .setState(rs.getString("state")).setRequestId(rs.getString("request_id")).setClaimToken(rs.getString("claim_token"))
            .setSnapshotJson(rs.getString("snapshot_json")).setResult(rs.getString("result")).setFailReason(rs.getString("fail_reason"))
            .setCreatedAt(rs.getTimestamp("created_at")).setUpdatedAt(rs.getTimestamp("updated_at"));

    private final RowMapper<AgentTaskEvent> eventMapper = (rs, rowNum) -> new AgentTaskEvent()
            .setId(rs.getLong("id")).setEventId(rs.getString("event_id")).setTaskId(rs.getString("task_id"))
            .setRunId(rs.getString("run_id")).setProcessRefId(rs.getLong("process_ref_id")).setAgentKey(rs.getString("agent_key"))
            .setEventType(rs.getString("event_type")).setActorType(rs.getString("actor_type")).setActorId(rs.getString("actor_id"))
            .setDecisionId(rs.getString("decision_id")).setDetailJson(rs.getString("detail_json")).setCreatedAt(rs.getTimestamp("created_at"));

    private final RowMapper<StatementCandidate> candidateMapper = (rs, rowNum) -> {
        Object pid = rs.getObject("pid");
        Object billJobId = rs.getObject("bill_job_id");
        return new StatementCandidate()
                .setId(rs.getLong("id"))
                .setPid(pid == null ? null : rs.getLong("pid"))
                .setPlatform(rs.getString("platform"))
                .setEntCode(rs.getString("ent_code"))
                .setState(rs.getString("state"))
                .setChargedMoney(rs.getBigDecimal("charged_money"))
                .setEmail(rs.getString("email"))
                .setManual(rs.getBoolean("manual"))
                .setBillJobId(billJobId == null ? null : rs.getLong("bill_job_id"))
                .setBillJobState(rs.getString("bill_job_state"));
    };
}
