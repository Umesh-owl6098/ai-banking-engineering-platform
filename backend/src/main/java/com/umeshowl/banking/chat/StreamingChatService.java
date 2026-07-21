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
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeSearchService knowledgeSearchService;
    private final OpenAiService openAiService;
    private final TaskExecutor taskExecutor;

    public StreamingChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            KnowledgeSearchService knowledgeSearchService,
            OpenAiService openAiService,
            TaskExecutor taskExecutor
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeSearchService = knowledgeSearchService;
        this.openAiService = openAiService;
        this.taskExecutor = taskExecutor;
    }

    public SseEmitter streamChat(
            ChatRequest request
    ) {

        validateRequest(request);

        SseEmitter emitter =
                new SseEmitter(STREAM_TIMEOUT);

        emitter.onTimeout(() -> {

            log.warn(
                    "Chat stream timed out for conversation {}",
                    request.conversationId()
            );

            emitter.complete();
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

        taskExecutor.execute(() ->
                processStream(request, emitter)
        );

        return emitter;
    }

    private void processStream(
            ChatRequest request,
            SseEmitter emitter
    ) {

        try {

            /*
             * The custom repository query loads the conversation,
             * agent and project in one database query.
             *
             * This prevents Hibernate LazyInitializationException
             * inside this asynchronous thread.
             */
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

            sendEvent(
                    emitter,
                    "metadata",
                    Map.of(
                            "conversationId",
                            conversation.getId(),
                            "userMessageId",
                            userMessage.getId(),
                            "startedAt",
                            LocalDateTime.now()
                    )
            );

            String completeAssistantResponse =
                    openAiService.generateReplyStreaming(
                            agent,
                            conversationHistory,
                            knowledgeContext,
                            token -> sendToken(
                                    emitter,
                                    token
                            )
                    );

            Message assistantMessage =
                    saveAssistantMessage(
                            conversation,
                            completeAssistantResponse
                    );

            sendEvent(
                    emitter,
                    "sources",
                    Map.of(
                            "sources",
                            sources
                    )
            );

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
                    LocalDateTime.now()
            );

            sendEvent(
                    emitter,
                    "complete",
                    completionData
            );

            emitter.complete();

        } catch (Exception exception) {

            log.error(
                    "Streaming chat failed for conversation {}",
                    request.conversationId(),
                    exception
            );

            handleStreamingError(
                    emitter,
                    exception
            );
        }
    }

    private void sendToken(
            SseEmitter emitter,
            String token
    ) {

        if (token == null || token.isEmpty()) {
            return;
        }

        try {

            sendEvent(
                    emitter,
                    "token",
                    Map.of(
                            "token",
                            token
                    )
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Client disconnected from the chat stream",
                    exception
            );
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

    private void handleStreamingError(
            SseEmitter emitter,
            Exception exception
    ) {

        try {

            sendEvent(
                    emitter,
                    "error",
                    Map.of(
                            "message",
                            getSafeErrorMessage(exception)
                    )
            );

            emitter.complete();

        } catch (Exception sendException) {

            log.debug(
                    "Could not send SSE error event: {}",
                    sendException.getMessage()
            );

            emitter.completeWithError(exception);
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
}