package com.umeshowl.banking.agent;

import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AiAgentService {

    private final AiAgentRepository aiAgentRepository;
    private final ProjectRepository projectRepository;

    public AiAgentService(
            AiAgentRepository aiAgentRepository,
            ProjectRepository projectRepository
    ) {
        this.aiAgentRepository = aiAgentRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public AiAgent createAgent(UUID projectId, CreateAiAgentRequest request) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found: " + projectId
                        )
                );

        AiAgent agent = new AiAgent();
        agent.setProject(project);
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setModel(request.getModel());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setTemperature(
                request.getTemperature() != null
                        ? request.getTemperature()
                        : 0.7
        );
        agent.setActive(
                request.getActive() == null || request.getActive()
        );

        return aiAgentRepository.save(agent);
    }

    @Transactional(readOnly = true)
    public List<AiAgent> getAgentsByProject(UUID projectId) {

        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException(
                    "Project not found: " + projectId
            );
        }

        return aiAgentRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public AiAgent getAgentById(UUID agentId) {

        return aiAgentRepository.findById(agentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "AI Agent not found: " + agentId
                        )
                );
    }

    @Transactional
    public AiAgent updateAgent(
            UUID agentId,
            UpdateAiAgentRequest request
    ) {

        AiAgent agent = aiAgentRepository.findById(agentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "AI Agent not found: " + agentId
                        )
                );

        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setModel(request.getModel());
        agent.setSystemPrompt(request.getSystemPrompt());

        agent.setTemperature(
                request.getTemperature() != null
                        ? request.getTemperature()
                        : 0.7
        );

        agent.setActive(
                request.getActive() == null
                        || request.getActive()
        );

        return aiAgentRepository.save(agent);
    }
}