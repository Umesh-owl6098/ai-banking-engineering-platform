package com.umeshowl.banking.chat;

import com.umeshowl.banking.auth.JwtService;
import com.umeshowl.banking.auth.Role;
import com.umeshowl.banking.auth.User;
import com.umeshowl.banking.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("security-test")
class ChatStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private StreamingChatService streamingChatService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User supervisor = new User();
        supervisor.setUsername("supervisor");
        supervisor.setPasswordHash(
                passwordEncoder.encode("Password123!")
        );
        supervisor.setRole(Role.SUPERVISOR);
        userRepository.save(supervisor);

        accessToken = jwtService.generateToken(supervisor);
    }

    @Test
    void asyncDispatchCompletesWithoutAuthorizationDenied() throws Exception {
        when(streamingChatService.streamChat(any())).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter();

            new Thread(() -> {
                try {
                    emitter.send(
                            SseEmitter.event()
                                    .name("complete")
                                    .data("""
                                            {"assistantMessageId":"%s"}
                                            """.formatted(UUID.randomUUID()))
                    );
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(ignored);
                }
            }).start();

            return emitter;
        });

        MvcResult result = mockMvc.perform(
                        post("/api/chat/stream")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("""
                                        {
                                          "conversationId":"%s",
                                          "message":"close my account"
                                        }
                                        """.formatted(UUID.randomUUID()))
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());
    }
}
