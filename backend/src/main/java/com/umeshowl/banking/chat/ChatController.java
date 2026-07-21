package com.umeshowl.banking.chat;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    private final StreamingChatService streamingChatService;

    public ChatController(
            ChatService chatService,
            StreamingChatService streamingChatService
    ) {
        this.chatService =
                chatService;

        this.streamingChatService =
                streamingChatService;
    }

    /*
     * Existing non-streaming endpoint.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request
    ) {

        return chatService.chat(
                request
        );
    }

    /*
     * New ChatGPT-style streaming endpoint.
     */
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamChat(
            @Valid @RequestBody ChatRequest request
    ) {

        return streamingChatService.streamChat(
                request
        );
    }
}