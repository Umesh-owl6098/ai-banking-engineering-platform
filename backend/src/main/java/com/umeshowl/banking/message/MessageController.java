package com.umeshowl.banking.message;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message create(
            @Valid @RequestBody CreateMessageRequest request
    ) {
        return messageService.create(request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<Message> getByConversation(
            @PathVariable UUID conversationId
    ) {
        return messageService.getByConversation(conversationId);
    }
}