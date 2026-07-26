package com.umeshowl.banking.conversation;

import com.umeshowl.banking.agent.AiAgent;
import com.umeshowl.banking.agent.AiAgentRepository;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ProjectRepository projectRepository;
    private final AiAgentRepository aiAgentRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ProjectRepository projectRepository,
            AiAgentRepository aiAgentRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.projectRepository = projectRepository;
        this.aiAgentRepository = aiAgentRepository;
    }

    public Conversation create(
            CreateConversationRequest request
    ) {
        Project project = projectRepository
                .findById(request.getProjectId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found"
                        )
                );

        AiAgent agent = aiAgentRepository
                .findById(request.getAgentId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "AI Agent not found"
                        )
                );

        Conversation conversation = new Conversation();
        conversation.setProject(project);
        conversation.setAgent(agent);
        conversation.setTitle(request.getTitle());

        return conversationRepository.save(conversation);
    }

    public List<Conversation> getByProject(
            UUID projectId
    ) {
        return conversationRepository
                .findByProjectId(projectId);
    }

    public List<Conversation> getByAgent(
            UUID agentId
    ) {
        return conversationRepository
                .findByAgentId(agentId);
    }

    public Conversation updateTitle(
            UUID conversationId,
            UpdateConversationTitleRequest request
    ) {
        Conversation conversation =
                conversationRepository
                        .findById(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found"
                                )
                        );

        String title = request.getTitle();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Conversation title is required"
            );
        }

        conversation.setTitle(title.trim());

        return conversationRepository.save(
                conversation
        );
    }

    @Transactional
    public void deleteConversation(
            UUID conversationId
    ) {
        Conversation conversation =
                conversationRepository
                        .findById(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found"
                                )
                        );

        conversationRepository.delete(conversation);
    }
}