CREATE TABLE investigation_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    project_id UUID NOT NULL,
    conversation_id UUID,

    customer_id UUID,
    transaction_id UUID,

    case_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    analyst_id VARCHAR(200),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_investigation_cases_conversation
        UNIQUE (conversation_id),

    CONSTRAINT fk_investigation_case_project
        FOREIGN KEY (project_id)
            REFERENCES projects(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_investigation_case_conversation
        FOREIGN KEY (conversation_id)
            REFERENCES conversations(id)
            ON DELETE SET NULL,

    CONSTRAINT fk_investigation_case_customer
        FOREIGN KEY (customer_id)
            REFERENCES mock_customers(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_investigation_case_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES mock_transactions(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_investigation_cases_subject
        CHECK (
            customer_id IS NOT NULL
            OR transaction_id IS NOT NULL
        ),

    CONSTRAINT chk_investigation_cases_type
        CHECK (
            case_type IN (
                'FRAUD',
                'KYC',
                'AML',
                'COMPLIANCE',
                'MULTI'
            )
        ),

    CONSTRAINT chk_investigation_cases_status
        CHECK (
            status IN (
                'OPEN',
                'INVESTIGATING',
                'AWAITING_REVIEW',
                'APPROVED',
                'REJECTED',
                'ESCALATED',
                'CLOSED'
            )
        ),

    CONSTRAINT chk_investigation_cases_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_investigation_cases_title
        CHECK (BTRIM(title) <> ''),

    CONSTRAINT chk_investigation_cases_description
        CHECK (BTRIM(description) <> '')
);

CREATE TABLE agent_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    case_id UUID NOT NULL,
    agent_id UUID,

    agent_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    risk_level VARCHAR(20),
    confidence NUMERIC(4, 3),

    summary TEXT,
    raw_llm_response TEXT,
    structured_json JSONB,
    rag_query TEXT,

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_agent_finding_case
        FOREIGN KEY (case_id)
            REFERENCES investigation_cases(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_agent_finding_agent
        FOREIGN KEY (agent_id)
            REFERENCES ai_agents(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_agent_findings_type
        CHECK (
            agent_type IN (
                'SUPERVISOR',
                'FRAUD',
                'KYC',
                'AML',
                'COMPLIANCE'
            )
        ),

    CONSTRAINT chk_agent_findings_status
        CHECK (
            status IN (
                'PENDING',
                'RUNNING',
                'COMPLETE',
                'FAILED',
                'PARSE_FAILED'
            )
        ),

    CONSTRAINT chk_agent_findings_risk_level
        CHECK (
            risk_level IS NULL
            OR risk_level IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_agent_findings_confidence
        CHECK (
            confidence IS NULL
            OR (confidence >= 0 AND confidence <= 1)
        ),

    CONSTRAINT chk_agent_findings_completion_time
        CHECK (
            completed_at IS NULL
            OR started_at IS NULL
            OR completed_at >= started_at
        )
);

CREATE TABLE agent_finding_citations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    finding_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,

    file_name VARCHAR(255) NOT NULL,
    chunk_index INT NOT NULL,
    similarity NUMERIC(6, 5),
    content_preview TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_agent_finding_citation_finding
        FOREIGN KEY (finding_id)
            REFERENCES agent_findings(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_agent_finding_citation_chunk
        FOREIGN KEY (chunk_id)
            REFERENCES document_chunks(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_agent_finding_citation_document
        FOREIGN KEY (document_id)
            REFERENCES knowledge_documents(id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_agent_finding_citation_chunk
        UNIQUE (finding_id, chunk_id),

    CONSTRAINT chk_agent_finding_citations_chunk_index
        CHECK (chunk_index >= 0),

    CONSTRAINT chk_agent_finding_citations_similarity
        CHECK (
            similarity IS NULL
            OR (similarity >= 0 AND similarity <= 1)
        )
);

CREATE TABLE investigation_case_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    case_id UUID NOT NULL,

    event_type VARCHAR(100) NOT NULL,
    actor VARCHAR(200),
    payload JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_investigation_case_event_case
        FOREIGN KEY (case_id)
            REFERENCES investigation_cases(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_investigation_case_events_type
        CHECK (
            event_type IN (
                'CASE_CREATED',
                'CASE_STATUS_CHANGED',
                'SUPERVISOR_ROUTING',
                'AGENT_INVOCATION_STARTED',
                'AGENT_FINDING_PRODUCED',
                'AGENT_FAILED',
                'COMPLIANCE_REVIEW_COMPLETE',
                'INVESTIGATION_COMPLETE',
                'HUMAN_DECISION',
                'CASE_CLOSED',
                'ANALYST_NOTE',
                'CLARIFICATION_REQUESTED'
            )
        )
);

CREATE TABLE human_review_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    case_id UUID NOT NULL,
    finding_id UUID,

    reviewer_id VARCHAR(200) NOT NULL,
    decision VARCHAR(50) NOT NULL,
    reason TEXT,

    decision_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_human_review_decision_case
        FOREIGN KEY (case_id)
            REFERENCES investigation_cases(id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_human_review_decision_finding
        FOREIGN KEY (finding_id)
            REFERENCES agent_findings(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_human_review_decisions_type
        CHECK (
            decision IN (
                'APPROVED',
                'REJECTED',
                'ESCALATED',
                'NOTE_ADDED'
            )
        ),

    CONSTRAINT chk_human_review_decisions_reason
        CHECK (
            decision NOT IN (
                'REJECTED',
                'ESCALATED',
                'NOTE_ADDED'
            )
            OR NULLIF(BTRIM(reason), '') IS NOT NULL
        )
);

CREATE INDEX idx_investigation_cases_project
    ON investigation_cases(project_id);

CREATE INDEX idx_investigation_cases_status
    ON investigation_cases(status);

CREATE INDEX idx_investigation_cases_project_status
    ON investigation_cases(project_id, status, created_at DESC);

CREATE INDEX idx_investigation_cases_priority
    ON investigation_cases(priority, created_at DESC)
    WHERE status NOT IN (
        'APPROVED',
        'REJECTED',
        'CLOSED'
    );

CREATE INDEX idx_investigation_cases_customer
    ON investigation_cases(customer_id)
    WHERE customer_id IS NOT NULL;

CREATE INDEX idx_investigation_cases_transaction
    ON investigation_cases(transaction_id)
    WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_agent_findings_case
    ON agent_findings(case_id);

CREATE INDEX idx_agent_findings_case_type
    ON agent_findings(case_id, agent_type);

CREATE INDEX idx_agent_findings_status
    ON agent_findings(status);

CREATE INDEX idx_agent_finding_citations_finding
    ON agent_finding_citations(finding_id);

CREATE INDEX idx_investigation_case_events_case_created
    ON investigation_case_events(case_id, created_at);

CREATE INDEX idx_investigation_case_events_type
    ON investigation_case_events(event_type);

CREATE INDEX idx_human_review_decisions_case
    ON human_review_decisions(case_id, decision_at);

CREATE UNIQUE INDEX uq_human_review_decisions_final_case
    ON human_review_decisions(case_id)
    WHERE decision IN (
        'APPROVED',
        'REJECTED'
    );
