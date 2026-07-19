package com.umeshowl.banking.agent;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/projects/{projectId}/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AiAgent createAgent(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateAiAgentRequest request
    ) {
        return aiAgentService.createAgent(projectId, request);
    }

    @GetMapping("/projects/{projectId}/agents")
    public List<AiAgent> getAgentsByProject(
            @PathVariable UUID projectId
    ) {
        return aiAgentService.getAgentsByProject(projectId);
    }

    @GetMapping("/agents/{agentId}")
    public AiAgent getAgentById(
            @PathVariable UUID agentId
    ) {
        return aiAgentService.getAgentById(agentId);
    }
}