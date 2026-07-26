package com.umeshowl.banking.investigation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "human_review_decisions")
@Getter
@Setter
@NoArgsConstructor
public class HumanReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private InvestigationCase investigationCase;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private AgentFinding finding;

    @Column(name = "reviewer_id", nullable = false, length = 200)
    private String reviewerId;

    @Column(nullable = false, length = 50)
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "decision_at", nullable = false, updatable = false)
    private OffsetDateTime decisionAt;

    @PrePersist
    public void beforeCreate() {
        decisionAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
