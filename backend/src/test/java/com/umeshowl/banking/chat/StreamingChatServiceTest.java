package com.umeshowl.banking.chat;

import com.umeshowl.banking.agent.AiAgent;
import com.umeshowl.banking.conversation.Conversation;
import com.umeshowl.banking.conversation.ConversationRepository;
import com.umeshowl.banking.knowledge.KnowledgeSearchService;
import com.umeshowl.banking.knowledge.dto.KnowledgeSearchResult;
import com.umeshowl.banking.message.Message;
import com.umeshowl.banking.message.MessageRepository;
import com.umeshowl.banking.message.MessageRole;
import com.umeshowl.banking.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreamingChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private KnowledgeSearchService knowledgeSearchService;

    @Mock
    private OpenAiService openAiService;

    private StreamingChatService streamingChatService;

    private UUID conversationId;
    private UUID projectId;
    private Conversation conversation;
    private AiAgent agent;

    @BeforeEach
    void setUp() {
        streamingChatService = new StreamingChatService(
                conversationRepository,
                messageRepository,
                knowledgeSearchService,
                openAiService,
                new SyncTaskExecutor()
        );

        conversationId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        agent = new AiAgent();
        agent.setModel("gpt-4.1-mini");
        agent.setSystemPrompt("Banking assistant");

        Project project = new Project();
        project.setId(projectId);

        conversation = mock(Conversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getAgent()).thenReturn(agent);
        when(conversation.getProject()).thenReturn(project);
    }

    @Test
    void executeStream_completesSuccessfullyAfterTokensAndSources() {
        stubConversationAndMessages();
        stubKnowledgeSearch();
        stubSavedMessages();

        when(openAiService.generateReplyStreaming(
                eq(agent),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            Consumer<String> tokenConsumer = invocation.getArgument(3);
            tokenConsumer.accept("Account ");
            tokenConsumer.accept("closure steps.");
            return "Account closure steps.";
        });

        CapturingSseEmitter emitter = new CapturingSseEmitter();

        streamingChatService.executeStreamForTests(
                new ChatRequest(conversationId, "close my account"),
                emitter
        );

        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:metadata")
        );
        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:token")
                        && event.contains("Account ")
        );
        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:sources")
        );
        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:complete")
        );
        assertThat(emitter.completed()).isTrue();
        assertThat(emitter.completedWithError()).isFalse();
    }

    @Test
    void executeStream_sendsStructuredErrorWhenConversationMissing() {
        when(conversationRepository.findByIdWithAgentAndProject(conversationId))
                .thenReturn(Optional.empty());

        CapturingSseEmitter emitter = new CapturingSseEmitter();

        streamingChatService.executeStreamForTests(
                new ChatRequest(conversationId, "close my account"),
                emitter
        );

        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:error")
                        && event.contains("Conversation not found")
                        && event.contains("VALIDATION_ERROR")
        );
        assertThat(emitter.completed()).isTrue();
        assertThat(emitter.completedWithError()).isFalse();
    }

    @Test
    void executeStream_usesNonStreamingFallbackWhenOpenAiStreamingFails() {
        stubConversationAndMessages();
        stubKnowledgeSearch();
        stubSavedMessages();

        when(openAiService.generateReplyStreaming(
                eq(agent),
                any(),
                any(),
                any()
        )).thenThrow(new IllegalStateException("OpenAI streaming request failed"));

        when(openAiService.generateReply(
                eq(agent),
                any(),
                any()
        )).thenReturn("Fallback account closure guidance.");

        CapturingSseEmitter emitter = new CapturingSseEmitter();

        streamingChatService.executeStreamForTests(
                new ChatRequest(conversationId, "close my account"),
                emitter
        );

        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:token")
                        && event.contains("Fallback account closure guidance.")
        );
        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:complete")
                        && event.contains("OPENAI_STREAMING_FALLBACK")
        );
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    void executeStream_usesDeterministicFallbackWhenAllOpenAiCallsFail() {
        stubConversationAndMessages();
        stubKnowledgeSearch();
        stubSavedMessages();

        when(openAiService.generateReplyStreaming(
                eq(agent),
                any(),
                any(),
                any()
        )).thenThrow(new IllegalStateException("OpenAI streaming request failed"));

        when(openAiService.generateReply(
                eq(agent),
                any(),
                any()
        )).thenThrow(new IllegalStateException("OpenAI request failed"));

        CapturingSseEmitter emitter = new CapturingSseEmitter();

        streamingChatService.executeStreamForTests(
                new ChatRequest(conversationId, "close my account"),
                emitter
        );

        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:token")
                        && event.contains("I could not complete an AI response right now.")
        );
        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:complete")
                        && event.contains("DETERMINISTIC_FALLBACK")
        );
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    void executeStream_stopsGracefullyWhenClientDisconnectsDuringTokens() {
        stubConversationAndMessages();
        stubKnowledgeSearch();
        stubSavedMessages();

        when(openAiService.generateReplyStreaming(
                eq(agent),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            Consumer<String> tokenConsumer = invocation.getArgument(3);
            tokenConsumer.accept("Partial ");
            tokenConsumer.accept("response");
            return "Partial response";
        });

        CapturingSseEmitter emitter = new CapturingSseEmitter();
        emitter.failAfterEvents(2);

        streamingChatService.executeStreamForTests(
                new ChatRequest(conversationId, "close my account"),
                emitter
        );

        assertThat(emitter.eventsAsText()).hasSize(2);
        assertThat(emitter.completedWithError()).isFalse();
    }

    @Test
    void executeStream_timeoutSendsStructuredErrorAndCompletes() {
        stubConversationAndMessages();
        stubKnowledgeSearch();
        stubSavedMessages();

        CapturingSseEmitter emitter = new CapturingSseEmitter();

        when(openAiService.generateReplyStreaming(
                eq(agent),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            emitter.runTimeoutHandler();
            return "late response";
        });

        streamingChatService.executeStreamForTests(
                new ChatRequest(conversationId, "close my account"),
                emitter
        );

        assertThat(emitter.eventsAsText()).anyMatch(event ->
                event.contains("event:error")
                        && event.contains("Chat stream timed out")
                        && event.contains("TIMEOUT")
        );
        assertThat(emitter.completed()).isTrue();
        assertThat(emitter.completedWithError()).isFalse();
    }

    private void stubConversationAndMessages() {
        when(conversationRepository.findByIdWithAgentAndProject(conversationId))
                .thenReturn(Optional.of(conversation));

        Message userHistoryMessage = new Message();
        userHistoryMessage.setRole(MessageRole.USER);
        userHistoryMessage.setContent("close my account");

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(userHistoryMessage));
    }

    private void stubKnowledgeSearch() {
        when(knowledgeSearchService.search(
                eq(projectId),
                any(),
                anyInt()
        )).thenReturn(List.of(
                new KnowledgeSearchResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "account-policy.pdf",
                        0,
                        "Account closure requires identity verification.",
                        0.91d
                )
        ));
    }

    private void stubSavedMessages() {
        Message savedUserMessage = mock(Message.class);
        when(savedUserMessage.getId()).thenReturn(UUID.randomUUID());
        when(savedUserMessage.getRole()).thenReturn(MessageRole.USER);
        when(savedUserMessage.getContent()).thenReturn("close my account");

        Message savedAssistantMessage = mock(Message.class);
        when(savedAssistantMessage.getId()).thenReturn(UUID.randomUUID());
        when(savedAssistantMessage.getRole()).thenReturn(MessageRole.ASSISTANT);
        when(savedAssistantMessage.getContent()).thenReturn("Account closure steps.");

        when(messageRepository.saveAndFlush(any(Message.class)))
                .thenReturn(savedUserMessage, savedAssistantMessage);
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final List<String> capturedEvents = new ArrayList<>();
        private Runnable timeoutHandler;
        private boolean completed;
        private boolean completedWithError;
        private int sendCount;
        private Integer failAfterEvents;

        private CapturingSseEmitter() {
            super(0L);
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutHandler = callback;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;

            if (failAfterEvents != null && sendCount > failAfterEvents) {
                throw new IOException("Client disconnected");
            }

            capturedEvents.add(formatEvent(builder));
        }

        private String formatEvent(SseEventBuilder builder) {
            StringBuilder rendered = new StringBuilder();

            for (var dataWithMediaType : builder.build()) {
                rendered.append(dataWithMediaType.getData());
            }

            return rendered.toString();
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable throwable) {
            completedWithError = true;
        }

        void failAfterEvents(int count) {
            failAfterEvents = count;
        }

        void runTimeoutHandler() {
            if (timeoutHandler != null) {
                timeoutHandler.run();
            }
        }

        List<String> eventsAsText() {
            return capturedEvents;
        }

        boolean completed() {
            return completed;
        }

        boolean completedWithError() {
            return completedWithError;
        }
    }
}
