package com.yuexin.bmp.agent.task.api;

import com.yuexin.bmp.agent.task.model.AgentRun;
import com.yuexin.bmp.agent.task.service.TaskGenerationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/runs")
public class AdminRunController {
    private final TaskGenerationService generationService;

    public AdminRunController(TaskGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public ApiDtos.RunResponse generate(@RequestBody ApiDtos.GenerateRunRequest request) {
        AgentRun run = generationService.generate(request.getPolicyId(), request.getActor());
        ApiDtos.RunResponse response = new ApiDtos.RunResponse();
        response.setRunId(run.getRunId());
        response.setMaxTasks(run.getMaxTasks());
        response.setStatus(run.getStatus());
        response.setGrantStatus(run.getGrantStatus());
        return response;
    }
}
