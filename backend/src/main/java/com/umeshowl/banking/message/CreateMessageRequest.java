package com.umeshowl.banking.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMessageRequest(

        @NotNull
        UUID conversationId,

        @NotNull
        MessageRole role,

        @NotBlank
        String content,

        Integer tokenCount
) {
}