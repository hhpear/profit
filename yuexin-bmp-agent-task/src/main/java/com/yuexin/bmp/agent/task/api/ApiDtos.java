package com.yuexin.bmp.agent.task.api;

import java.util.ArrayList;
import java.util.List;

public class ApiDtos {
    public static class GenerateRunRequest {
        private Long policyId;
        private String actor;
        public Long getPolicyId() { return policyId; }
        public void setPolicyId(Long policyId) { this.policyId = policyId; }
        public String getActor() { return actor; }
        public void setActor(String actor) { this.actor = actor; }
    }

    public static class ClaimTasksRequest {
        private String runId;
        private String agentKey;
        private int limit = 10;
        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public String getAgentKey() { return agentKey; }
        public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
    }

    public static class ExecuteTaskRequest {
        private String taskId;
        private String requestId;
        private String agentKey;
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getAgentKey() { return agentKey; }
        public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    }

    public static class TaskListResponse {
        private List<TaskResponse> tasks = new ArrayList<>();
        public List<TaskResponse> getTasks() { return tasks; }
        public void setTasks(List<TaskResponse> tasks) { this.tasks = tasks; }
    }

    public static class TaskResponse {
        private String taskId;
        private String runId;
        private Long processRefId;
        private Long bizRefId;
        private String agentKey;
        private String bizType;
        private String state;
        private String result;
        private String failReason;
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public Long getProcessRefId() { return processRefId; }
        public void setProcessRefId(Long processRefId) { this.processRefId = processRefId; }
        public Long getBizRefId() { return bizRefId; }
        public void setBizRefId(Long bizRefId) { this.bizRefId = bizRefId; }
        public String getAgentKey() { return agentKey; }
        public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
        public String getBizType() { return bizType; }
        public void setBizType(String bizType) { this.bizType = bizType; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getFailReason() { return failReason; }
        public void setFailReason(String failReason) { this.failReason = failReason; }
    }

    public static class RunResponse {
        private String runId;
        private int maxTasks;
        private String status;
        private String grantStatus;
        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public int getMaxTasks() { return maxTasks; }
        public void setMaxTasks(int maxTasks) { this.maxTasks = maxTasks; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getGrantStatus() { return grantStatus; }
        public void setGrantStatus(String grantStatus) { this.grantStatus = grantStatus; }
    }
}
