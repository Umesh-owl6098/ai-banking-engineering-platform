package com.umeshowl.banking.investigation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.umeshowl.banking.conversation.Conversation;
import com.umeshowl.banking.mockdata.MockCustomer;
import com.umeshowl.banking.mockdata.MockTransaction;
import com.umeshowl.banking.project.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "investigation_cases")
@Getter
@Setter
@NoArgsConstructor
public class InvestigationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", unique = true)
    private Conversation conversation;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private MockCustomer customer;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private MockTransaction transaction;

    @Column(name = "case_type", nullable = false, length = 50)
    private String caseType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "analyst_id", length = 200)
    private String analystId;

    @Column(name = "auto_created", nullable = false)
    private boolean autoCreated;

    @Column(name = "screening_status", length = 50)
    private String screeningStatus;

    @Column(name = "screening_reason", length = 500)
    private String screeningReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "screening_triggered_rules")
    private String[] screeningTriggeredRules;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "execution_failure_stage", length = 100)
    private String executionFailureStage;

    @Column(name = "execution_failure_message", length = 500)
    private String executionFailureMessage;

    @Column(name = "execution_failure_at")
    private OffsetDateTime executionFailureAt;

    @Column(name = "scenario_group_id", length = 100)
    private String scenarioGroupId;

    @Column(name = "assigned_analyst_id")
    private UUID assignedAnalystId;

    @Column(name = "assigned_analyst_username", length = 100)
    private String assignedAnalystUsername;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "review_started_at")
    private OffsetDateTime reviewStartedAt;

    @Column(name = "assignment_notes", columnDefinition = "TEXT")
    private String assignmentNotes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "investigationCase", fetch = FetchType.LAZY)
    private List<AgentFinding> agentFindings = new ArrayList<>();

    @OneToMany(mappedBy = "investigationCase", fetch = FetchType.LAZY)
    private List<InvestigationCaseEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "investigationCase", fetch = FetchType.LAZY)
    private List<HumanReviewDecision> humanReviewDecisions = new ArrayList<>();

    @PrePersist
    public void beforeCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (status == null) {
            status = "OPEN";
        }

        if (priority == null) {
            priority = "MEDIUM";
        }

        if (!autoCreated) {
            autoCreated = false;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void beforeUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
