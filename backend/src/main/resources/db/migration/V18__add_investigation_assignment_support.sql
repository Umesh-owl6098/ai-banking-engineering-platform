ALTER TABLE investigation_cases
    ADD COLUMN assigned_analyst_id UUID,
    ADD COLUMN assigned_analyst_username VARCHAR(100),
    ADD COLUMN assigned_at TIMESTAMPTZ,
    ADD COLUMN review_started_at TIMESTAMPTZ,
    ADD COLUMN assignment_notes TEXT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE investigation_cases
    ADD CONSTRAINT fk_investigation_cases_assigned_analyst
        FOREIGN KEY (assigned_analyst_id)
            REFERENCES users(id)
            ON DELETE SET NULL;

ALTER TABLE investigation_cases
    DROP CONSTRAINT chk_investigation_cases_status;

ALTER TABLE investigation_cases
    ADD CONSTRAINT chk_investigation_cases_status
        CHECK (
            status IN (
                'NEW',
                'RUNNING',
                'REPORT_GENERATED',
                'EXECUTION_FAILED',
                'OPEN',
                'INVESTIGATING',
                'AWAITING_REVIEW',
                'ASSIGNED',
                'IN_REVIEW',
                'APPROVED',
                'REJECTED',
                'ESCALATED',
                'CLOSED'
            )
        );

ALTER TABLE investigation_case_events
    DROP CONSTRAINT chk_investigation_case_events_type;

ALTER TABLE investigation_case_events
    ADD CONSTRAINT chk_investigation_case_events_type
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
                'CLARIFICATION_REQUESTED',
                'INVESTIGATION_ASSIGNED',
                'INVESTIGATION_CLAIMED',
                'INVESTIGATION_REASSIGNED',
                'INVESTIGATION_UNASSIGNED',
                'ANALYST_REVIEW_STARTED'
            )
        );

CREATE INDEX idx_investigation_cases_assigned_analyst
    ON investigation_cases(assigned_analyst_id);

CREATE INDEX idx_investigation_cases_assignment_status
    ON investigation_cases(status, assigned_analyst_id);
