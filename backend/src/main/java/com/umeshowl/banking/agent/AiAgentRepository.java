package com.umeshowl.banking.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiAgentRepository extends JpaRepository<AiAgent, UUID> {

    List<AiAgent> findByProjectId(UUID projectId);
}