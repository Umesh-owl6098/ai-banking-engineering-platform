package com.umeshowl.banking.message;

import com.umeshowl.banking.conversation.Conversation;
import com.umeshowl.banking.conversation.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public Message create(CreateMessageRequest request) {

        Conversation conversation = conversationRepository
                .findById(request.conversationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + request.conversationId()
                ));

        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(request.role());
        message.setContent(request.content());
        message.setTokenCount(request.tokenCount());

        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<Message> getByConversation(UUID conversationId) {

        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException(
                    "Conversation not found: " + conversationId
            );
        }

        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}