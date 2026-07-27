package com.umeshowl.banking.chat;

import com.umeshowl.banking.agent.AiAgent;
import com.umeshowl.banking.conversation.Conversation;
import com.umeshowl.banking.conversation.ConversationRepository;
import com.umeshowl.banking.knowledge.KnowledgeSearchService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import com.umeshowl.banking.message.Message;
import com.umeshowl.banking.message.MessageRepository;
import com.umeshowl.banking.message.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class StreamingChatService {

    private static final Logger log =
            LoggerFactory.getLogger(StreamingChatService.class);

    private static final long STREAM_TIMEOUT =
            5 * 60 * 1000L;

    private static final int RAG_RESULT_LIMIT = 5;

    private static final int RETRIEVAL_HISTORY_LIMIT = 6;

    private static final int SOURCE_PREVIEW_LENGTH = 250;

    private static final int MAX_RETRIEVAL_MESSAGE_LENGTH = 1000;

    private static final String FALLBACK_ASSISTANT_MESSAGE =
            """
            I could not complete an AI response right now. \
            Please try again in a moment, or ask your question using \
            the standard banking knowledge available in the uploaded documents.
            """;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeSearchService knowledgeSearchService;
    private final OpenAiService openAiService;
    private final TaskExecutor chatStreamExecutor;

    public StreamingChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            KnowledgeSearchService knowledgeSearchService,
            OpenAiService openAiService,
            @Qualifier("chatStreamExecutor")
            TaskExecutor chatStreamExecutor
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeSearchService = knowledgeSearchService;
        this.openAiService = openAiService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    public SseEmitter streamChat(
            ChatRequest request
    ) {

        validateRequest(request);

        SseEmitter emitter =
                new SseEmitter(STREAM_TIMEOUT);

        AtomicBoolean streamClosed =
                new AtomicBoolean(false);

        registerEmitterCallbacks(
                request,
                emitter,
                streamClosed
        );

        SecurityContext securityContext =
                SecurityContextHolder.getContext();

        chatStreamExecutor.execute(() -> {

            SecurityContextHolder.setContext(
                    securityContext
            );

            try {
                executeStream(
                        request,
                        emitter,
                        streamClosed
                );
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    void executeStreamForTests(
            ChatRequest request,
            SseEmitter emitter
    ) {

        AtomicBoolean streamClosed =
                new AtomicBoolean(false);

        registerEmitterCallbacks(
                request,
                emitter,
                streamClosed
        );

        executeStream(
                request,
                emitter,
                streamClosed
        );
    }

    private void registerEmitterCallbacks(
            ChatRequest request,
            SseEmitter emitter,
            AtomicBoolean streamClosed
    ) {

        emitter.onTimeout(() -> {

            log.warn(
                    "Chat stream timed out for conversation {}",
                    request.conversationId()
            );

            sendErrorEvent(
                    emitter,
                    streamClosed,
                    "Chat stream timed out",
                    "TIMEOUT"
            );
        });

        emitter.onCompletion(() ->
                log.debug(
                        "Chat stream completed for conversation {}",
                        request.conversationId()
                )
        );

        emitter.onError(exception ->
                log.warn(
                        "Chat stream connection failed for conversation {}: {}",
                        request.conversationId(),
                        exception.getMessage()
                )
        );
    }

    private void executeStream(
            ChatRequest request,
            SseEmitter emitter,
            AtomicBoolean streamClosed
    ) {

        processStream(
                request,
                emitter,
                streamClosed
        );
    }

    private void processStream(
            ChatRequest request,
            SseEmitter emitter,
            AtomicBoolean streamClosed
    ) {

        try {

            Conversation conversation =
                    conversationRepository
                            .findByIdWithAgentAndProject(
                                    request.conversationId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Conversation not found: "
                                                    + request.conversationId()
                                    )
                            );

            AiAgent agent =
                    conversation.getAgent();

            UUID projectId =
                    conversation
                            .getProject()
                            .getId();

            Message userMessage =
                    saveUserMessage(
                            conversation,
                            request.message().trim()
                    );

            List<Message> conversationHistory =
                    messageRepository
                            .findByConversationIdOrderByCreatedAtAsc(
                                    conversation.getId()
                            );

            String retrievalQuery =
                    buildRetrievalQuery(
                            conversationHistory
                    );

            List<KnowledgeSearchResult> searchResults =
                    knowledgeSearchService.search(
                            projectId,
                            retrievalQuery,
                            RAG_RESULT_LIMIT
                    );

            List<SourceReference> sources =
                    buildSourceReferences(
                            searchResults
                    );

            String knowledgeContext =
                    buildKnowledgeContext(
                            searchResults
                    );

            if (!sendEventSafely(
                    emitter,
                    streamClosed,
                    "metadata",
                    Map.of(
                            "conversationId",
                            conversation.getId(),
                            "userMessageId",
                            userMessage.getId(),
                            "startedAt",
                            Instant.now().toString()
                    )
            )) {
                return;
            }

            AssistantGenerationResult generationResult =
                    generateAssistantResponse(
                            emitter,
                            streamClosed,
                            agent,
                            conversationHistory,
                            knowledgeContext
                    );

            if (streamClosed.get()) {
                return;
            }

            Message assistantMessage =
                    saveAssistantMessage(
                            conversation,
                            generationResult.content()
                    );

            if (!sendEventSafely(
                    emitter,
                    streamClosed,
                    "sources",
                    Map.of(
                            "sources",
                            sources
                    )
            )) {
                return;
            }

            Map<String, Object> completionData =
                    new LinkedHashMap<>();

            completionData.put(
                    "conversationId",
                    conversation.getId()
            );

            completionData.put(
                    "userMessageId",
                    userMessage.getId()
            );

            completionData.put(
                    "assistantMessageId",
                    assistantMessage.getId()
            );

            completionData.put(
                    "createdAt",
                    Instant.now().toString()
            );

            if (generationResult.usedFallback()) {
                completionData.put(
                        "fallback",
                        true
                );

                completionData.put(
                        "fallbackReason",
                        generationResult.fallbackReason()
                );
            }

            sendEventSafely(
                    emitter,
                    streamClosed,
                    "complete",
                    completionData
            );

            completeStream(
                    emitter,
                    streamClosed
            );

        } catch (Exception exception) {

            log.error(
                    "Streaming chat failed for conversation {}",
                    request.conversationId(),
                    exception
            );

            sendErrorEvent(
                    emitter,
                    streamClosed,
                    getSafeErrorMessage(exception),
                    resolveErrorCode(exception)
            );
        }
    }

    private AssistantGenerationResult generateAssistantResponse(
            SseEmitter emitter,
            AtomicBoolean streamClosed,
            AiAgent agent,
            List<Message> conversationHistory,
            String knowledgeContext
    ) {

        Consumer<String> tokenConsumer =
                token -> sendToken(
                        emitter,
                        streamClosed,
                        token
                );

        try {

            String completeAssistantResponse =
                    openAiService.generateReplyStreaming(
                            agent,
                            conversationHistory,
                            knowledgeContext,
                            tokenConsumer
                    );

            return new AssistantGenerationResult(
                    completeAssistantResponse,
                    false,
                    null
            );

        } catch (Exception streamingException) {

            log.warn(
                    "OpenAI streaming failed, attempting non-streaming fallback: {}",
                    streamingException.getMessage()
            );

            try {

                String fallbackResponse =
                        openAiService.generateReply(
                                agent,
                                conversationHistory,
                                knowledgeContext
                        );

                sendToken(
                        emitter,
                        streamClosed,
                        fallbackResponse
                );

                return new AssistantGenerationResult(
                        fallbackResponse,
                        true,
                        "OPENAI_STREAMING_FALLBACK"
                );

            } catch (Exception fallbackException) {

                log.warn(
                        "OpenAI non-streaming fallback failed, using deterministic response: {}",
                        fallbackException.getMessage()
                );

                sendToken(
                        emitter,
                        streamClosed,
                        FALLBACK_ASSISTANT_MESSAGE
                );

                return new AssistantGenerationResult(
                        FALLBACK_ASSISTANT_MESSAGE,
                        true,
                        "DETERMINISTIC_FALLBACK"
                );
            }
        }
    }

    private void sendToken(
            SseEmitter emitter,
            AtomicBoolean streamClosed,
            String token
    ) {

        if (streamClosed.get()
                || token == null
                || token.isEmpty()) {

            return;
        }

        sendEventSafely(
                emitter,
                streamClosed,
                "token",
                Map.of(
                        "token",
                        token
                )
        );
    }

    private boolean sendEventSafely(
            SseEmitter emitter,
            AtomicBoolean streamClosed,
            String eventName,
            Object data
    ) {

        if (streamClosed.get()) {
            return false;
        }

        try {

            sendEvent(
                    emitter,
                    eventName,
                    data
            );

            return true;

        } catch (IOException exception) {

            log.debug(
                    "Client disconnected while sending chat SSE event {}",
                    eventName
            );

            streamClosed.set(true);

            return false;

        } catch (IllegalStateException exception) {

            log.debug(
                    "Chat SSE emitter already closed while sending event {}",
                    eventName
            );

            streamClosed.set(true);

            return false;
        }
    }

    private void sendEvent(
            SseEmitter emitter,
            String eventName,
            Object data
    ) throws IOException {

        emitter.send(
                SseEmitter.event()
                        .name(eventName)
                        .data(data)
        );
    }

    private void sendErrorEvent(
            SseEmitter emitter,
            AtomicBoolean streamClosed,
            String message,
            String code
    ) {

        if (streamClosed.get()) {
            return;
        }

        try {

            sendEvent(
                    emitter,
                    "error",
                    Map.of(
                            "message",
                            message,
                            "code",
                            code
                    )
            );

        } catch (Exception sendException) {

            log.debug(
                    "Could not send SSE error event: {}",
                    sendException.getMessage()
            );
        }

        completeStream(
                emitter,
                streamClosed
        );
    }

    private void completeStream(
            SseEmitter emitter,
            AtomicBoolean streamClosed
    ) {

        if (!streamClosed.compareAndSet(false, true)) {
            return;
        }

        try {
            emitter.complete();
        } catch (Exception exception) {
            log.debug(
                    "Chat SSE emitter already completed: {}",
                    exception.getMessage()
            );
        }
    }

    private List<SourceReference> buildSourceReferences(
            List<KnowledgeSearchResult> results
    ) {

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .map(result ->
                        new SourceReference(
                                result.fileName(),
                                result.chunkIndex(),
                                result.similarity(),
                                createPreview(
                                        result.content()
                                )
                        )
                )
                .toList();
    }

    private String createPreview(
            String content
    ) {

        if (content == null || content.isBlank()) {
            return "";
        }

        String normalizedContent =
                content.trim()
                        .replaceAll("\\s+", " ");

        if (normalizedContent.length()
                <= SOURCE_PREVIEW_LENGTH) {

            return normalizedContent;
        }

        return normalizedContent.substring(
                0,
                SOURCE_PREVIEW_LENGTH
        ) + "...";
    }

    private String buildRetrievalQuery(
            List<Message> conversationHistory
    ) {

        if (conversationHistory == null
                || conversationHistory.isEmpty()) {

            throw new IllegalArgumentException(
                    "Conversation history cannot be empty"
            );
        }

        int startIndex =
                Math.max(
                        0,
                        conversationHistory.size()
                                - RETRIEVAL_HISTORY_LIMIT
                );

        List<Message> recentMessages =
                conversationHistory.subList(
                        startIndex,
                        conversationHistory.size()
                );

        StringBuilder queryBuilder =
                new StringBuilder();

        queryBuilder.append(
                "Use the following recent conversation to understand "
                        + "the meaning of the current banking question.\n\n"
        );

        for (Message message : recentMessages) {

            if (message == null
                    || message.getContent() == null
                    || message.getContent().isBlank()) {

                continue;
            }

            if (message.getRole() == MessageRole.USER) {

                queryBuilder.append("User: ");

            } else if (
                    message.getRole()
                            == MessageRole.ASSISTANT
            ) {

                queryBuilder.append("Assistant: ");

            } else {

                continue;
            }

            queryBuilder.append(
                    limitMessageLength(
                            message.getContent()
                    )
            );

            queryBuilder.append("\n");
        }

        queryBuilder.append(
                "\nRetrieve banking policy information relevant "
                        + "to the latest user question."
        );

        return queryBuilder.toString();
    }

    private String limitMessageLength(
            String content
    ) {

        String normalizedContent =
                content.trim();

        if (normalizedContent.length()
                <= MAX_RETRIEVAL_MESSAGE_LENGTH) {

            return normalizedContent;
        }

        return normalizedContent.substring(
                0,
                MAX_RETRIEVAL_MESSAGE_LENGTH
        );
    }

    private String buildKnowledgeContext(
            List<KnowledgeSearchResult> results
    ) {

        if (results == null || results.isEmpty()) {

            return """
                    No relevant information was found in the uploaded
                    knowledge base for this question.
                    """;
        }

        StringBuilder context =
                new StringBuilder();

        for (
                int index = 0;
                index < results.size();
                index++
        ) {

            KnowledgeSearchResult result =
                    results.get(index);

            context.append("[Source ")
                    .append(index + 1)
                    .append("]\n");

            context.append("File: ")
                    .append(result.fileName())
                    .append("\n");

            context.append("Chunk: ")
                    .append(result.chunkIndex())
                    .append("\n");

            context.append("Similarity: ")
                    .append(
                            String.format(
                                    "%.4f",
                                    result.similarity()
                            )
                    )
                    .append("\n");

            context.append("Content:\n")
                    .append(result.content())
                    .append("\n\n");
        }

        return context.toString();
    }

    private Message saveUserMessage(
            Conversation conversation,
            String content
    ) {

        Message message =
                new Message();

        message.setConversation(conversation);
        message.setRole(MessageRole.USER);
        message.setContent(content);

        return messageRepository.saveAndFlush(
                message
        );
    }

    private Message saveAssistantMessage(
            Conversation conversation,
            String content
    ) {

        Message message =
                new Message();

        message.setConversation(conversation);
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(content);

        return messageRepository.saveAndFlush(
                message
        );
    }

    private String getSafeErrorMessage(
            Exception exception
    ) {

        if (exception.getMessage() == null
                || exception.getMessage().isBlank()) {

            return "Streaming chat failed";
        }

        return exception.getMessage();
    }

    private String resolveErrorCode(
            Exception exception
    ) {

        if (exception instanceof IllegalArgumentException) {
            return "VALIDATION_ERROR";
        }

        if (exception instanceof IllegalStateException) {
            return "UPSTREAM_ERROR";
        }

        return "STREAM_ERROR";
    }

    private void validateRequest(
            ChatRequest request
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Chat request is required"
            );
        }

        if (request.conversationId() == null) {

            throw new IllegalArgumentException(
                    "Conversation ID is required"
            );
        }

        if (request.message() == null
                || request.message().isBlank()) {

            throw new IllegalArgumentException(
                    "Message cannot be empty"
            );
        }

        if (request.message().length() > 4000) {

            throw new IllegalArgumentException(
                    "Message cannot exceed 4000 characters"
            );
        }
    }

    private record AssistantGenerationResult(
            String content,
            boolean usedFallback,
            String fallbackReason
    ) {
    }
}
