package com.umeshowl.banking.investigation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.umeshowl.banking.knowledge.DocumentChunk;
import com.umeshowl.banking.knowledge.KnowledgeDocument;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "agent_finding_citations")
@Getter
@Setter
@NoArgsConstructor
public class AgentFindingCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private AgentFinding finding;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chunk_id", nullable = false)
    private DocumentChunk chunk;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(precision = 6, scale = 5)
    private BigDecimal similarity;

    @Column(name = "content_preview", columnDefinition = "TEXT")
    private String contentPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void beforeCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
