package com.umeshowl.banking.conversation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create(
            @RequestBody CreateConversationRequest request
    ) {
        return conversationService.create(request);
    }

    @GetMapping("/projects/{projectId}/conversations")
    public List<Conversation> getByProject(
            @PathVariable UUID projectId
    ) {
        return conversationService.getByProject(projectId);
    }

    @GetMapping("/agents/{agentId}/conversations")
    public List<Conversation> getByAgent(
            @PathVariable UUID agentId
    ) {
        return conversationService.getByAgent(agentId);
    }

    @PutMapping("/conversations/{conversationId}/title")
    public Conversation updateTitle(
            @PathVariable UUID conversationId,
            @RequestBody UpdateConversationTitleRequest request
    ) {
        return conversationService.updateTitle(
                conversationId,
                request
        );
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(
            @PathVariable UUID conversationId
    ) {
        conversationService.deleteConversation(
                conversationId
        );
    }
}