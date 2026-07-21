package com.umeshowl.banking.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        UUID userMessageId,
        UUID assistantMessageId,
        String userMessage,
        String assistantMessage,
        List<SourceReference> sources,
        LocalDateTime createdAt
) {
}