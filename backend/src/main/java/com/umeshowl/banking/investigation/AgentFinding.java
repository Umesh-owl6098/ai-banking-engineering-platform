package com.umeshowl.banking.investigation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.umeshowl.banking.agent.AiAgent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_findings")
@Getter
@Setter
@NoArgsConstructor
public class AgentFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private AiAgent agent;

    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "raw_llm_response", columnDefinition = "TEXT")
    private String rawLlmResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_json", columnDefinition = "jsonb")
    private String structuredJson;

    @Column(name = "rag_query", columnDefinition = "TEXT")
    private String ragQuery;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "finding", fetch = FetchType.LAZY)
    private List<AgentFindingCitation> citations = new ArrayList<>();

    @PrePersist
    public void beforeCreate() {
        if (status == null) {
            status = "PENDING";
        }

        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
