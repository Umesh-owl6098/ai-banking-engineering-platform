package com.umeshowl.banking.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository
        extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByProjectId(UUID projectId);

    List<Conversation> findByAgentId(UUID agentId);

    @Query("""
            SELECT DISTINCT conversation
            FROM Conversation conversation
            LEFT JOIN FETCH conversation.agent
            JOIN FETCH conversation.project
            WHERE conversation.id = :conversationId
            """)
    Optional<Conversation> findByIdWithAgentAndProject(
            @Param("conversationId") UUID conversationId
    );
}