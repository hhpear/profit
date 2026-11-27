package com.yuexin.bmp.agent.task.api;

import com.yuexin.bmp.agent.task.model.AgentTask;
import com.yuexin.bmp.agent.task.service.TaskExecutionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/tasks")
public class InternalTaskController {
    private final TaskExecutionService executionService;

    public InternalTaskController(TaskExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/claim")
    public ApiDtos.TaskListResponse claim(@RequestBody ApiDtos.ClaimTasksRequest request) {
        List<AgentTask> tasks = executionService.claim(request.getRunId(), request.getAgentKey(), request.getLimit());
        ApiDtos.TaskListResponse response = new ApiDtos.TaskListResponse();
        for (AgentTask task : tasks) {
            response.getTasks().add(convert(task));
        }
        return response;
    }

    @PostMapping("/execute")
    public ApiDtos.TaskResponse execute(@RequestBody ApiDtos.ExecuteTaskRequest request) {
        return convert(executionService.execute(request.getTaskId(), request.getRequestId(), request.getAgentKey()));
    }

    @GetMapping("/{taskId}")
    public ApiDtos.TaskResponse get(@PathVariable String taskId) {
        throw new UnsupportedOperationException("task query is not implemented yet");
    }

    private ApiDtos.TaskResponse convert(AgentTask task) {
        ApiDtos.TaskResponse response = new ApiDtos.TaskResponse();
        response.setTaskId(task.getTaskId());
        response.setRunId(task.getRunId());
        response.setProcessRefId(task.getProcessRefId());
        response.setBizRefId(task.getBizRefId());
        response.setAgentKey(task.getAgentKey());
        response.setBizType(task.getBizType());
        response.setState(task.getState());
        response.setResult(task.getResult());
        response.setFailReason(task.getFailReason());
        return response;
    }
}
