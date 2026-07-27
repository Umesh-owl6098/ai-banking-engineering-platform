package com.umeshowl.banking.conversation;

import com.umeshowl.banking.agent.AiAgent;
import com.umeshowl.banking.agent.AiAgentRepository;
import com.umeshowl.banking.project.Project;
import com.umeshowl.banking.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AiAgentRepository aiAgentRepository;

    @InjectMocks
    private ConversationService conversationService;

    private UUID projectId;
    private UUID agentId;
    private UUID otherProjectId;
    private Project project;
    private Project otherProject;
    private AiAgent agent;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        agentId = UUID.randomUUID();
        otherProjectId = UUID.randomUUID();

        project = new Project();
        project.setId(projectId);

        otherProject = new Project();
        otherProject.setId(otherProjectId);

        agent = new AiAgent();
        agent.setId(agentId);
        agent.setProject(project);
    }

    @Test
    void create_throwsWhenAgentNotFound() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setProjectId(projectId);
        request.setAgentId(agentId);
        request.setTitle("New Banking Conversation");

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(aiAgentRepository.findById(agentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI Agent not found");
    }

    @Test
    void create_throwsWhenAgentBelongsToDifferentProject() {
        agent.setProject(otherProject);

        CreateConversationRequest request = new CreateConversationRequest();
        request.setProjectId(projectId);
        request.setAgentId(agentId);
        request.setTitle("New Banking Conversation");

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(aiAgentRepository.findById(agentId))
                .thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> conversationService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "AI Agent does not belong to the specified project"
                );
    }
}
