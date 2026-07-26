package com.umeshowl.banking.chat;

import com.umeshowl.banking.conversation.Conversation;
import com.umeshowl.banking.conversation.ConversationRepository;
import com.umeshowl.banking.knowledge.KnowledgeSearchService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import com.umeshowl.banking.message.Message;
import com.umeshowl.banking.message.MessageRepository;
import com.umeshowl.banking.message.MessageRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final int RAG_RESULT_LIMIT = 5;
    private static final int RETRIEVAL_HISTORY_LIMIT = 6;
    private static final int SOURCE_PREVIEW_LENGTH = 250;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAiService openAiService;
    private final KnowledgeSearchService knowledgeSearchService;

    public ChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            OpenAiService openAiService,
            KnowledgeSearchService knowledgeSearchService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.openAiService = openAiService;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request) {

        validateRequest(request);

        Conversation conversation = conversationRepository
                .findById(request.conversationId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found: "
                                        + request.conversationId()
                        )
                );

        Message userMessage = saveUserMessage(
                conversation,
                request.message().trim()
        );

        List<Message> conversationHistory =
                messageRepository
                        .findByConversationIdOrderByCreatedAtAsc(
                                conversation.getId()
                        );

        String retrievalQuery =
                buildRetrievalQuery(conversationHistory);

        UUID projectId =
                conversation.getProject().getId();

        List<KnowledgeSearchResult> searchResults =
                knowledgeSearchService.search(
                        projectId,
                        retrievalQuery,
                        RAG_RESULT_LIMIT
                );

        List<SourceReference> sources =
                buildSourceReferences(searchResults);

        String knowledgeContext =
                buildKnowledgeContext(searchResults);

        String aiReply =
                openAiService.generateReply(
                        conversation.getAgent(),
                        conversationHistory,
                        knowledgeContext
                );

        Message assistantMessage =
                saveAssistantMessage(
                        conversation,
                        aiReply
                );

        return new ChatResponse(
                conversation.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                userMessage.getContent(),
                assistantMessage.getContent(),
                sources,
                LocalDateTime.now()
        );
    }

    private List<SourceReference> buildSourceReferences(
            List<KnowledgeSearchResult> searchResults
    ) {

        if (searchResults == null
                || searchResults.isEmpty()) {

            return List.of();
        }

        return searchResults.stream()
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

    private String createPreview(String content) {

        if (content == null
                || content.isBlank()) {

            return "";
        }

        String normalizedContent =
                content.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

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

        int startIndex = Math.max(
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

            if (message.getContent() == null
                    || message.getContent().isBlank()) {

                continue;
            }

            if (message.getRole()
                    == MessageRole.USER) {

                queryBuilder.append("User: ");

            } else if (message.getRole()
                    == MessageRole.ASSISTANT) {

                queryBuilder.append(
                        "Assistant: "
                );

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

        int maximumLength = 1000;

        if (normalizedContent.length()
                <= maximumLength) {

            return normalizedContent;
        }

        return normalizedContent.substring(
                0,
                maximumLength
        );
    }

    private String buildKnowledgeContext(
            List<KnowledgeSearchResult> results
    ) {

        if (results == null
                || results.isEmpty()) {

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

        Message userMessage =
                new Message();

        userMessage.setConversation(
                conversation
        );

        userMessage.setRole(
                MessageRole.USER
        );

        userMessage.setContent(
                content
        );

        return messageRepository.save(
                userMessage
        );
    }

    private Message saveAssistantMessage(
            Conversation conversation,
            String content
    ) {

        Message assistantMessage =
                new Message();

        assistantMessage.setConversation(
                conversation
        );

        assistantMessage.setRole(
                MessageRole.ASSISTANT
        );

        assistantMessage.setContent(
                content
        );

        return messageRepository.save(
                assistantMessage
        );
    }

    private void validateRequest(
            ChatRequest request
    ) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Chat request is required"
            );
        }

        if (request.conversationId()
                == null) {

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

        if (request.message().length()
                > 4000) {

            throw new IllegalArgumentException(
                    "Message cannot exceed 4000 characters"
            );
        }
    }
}