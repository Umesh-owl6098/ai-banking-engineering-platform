ALTER TABLE investigation_cases
    ADD COLUMN execution_failure_stage VARCHAR(100),
    ADD COLUMN execution_failure_message VARCHAR(500),
    ADD COLUMN execution_failure_at TIMESTAMPTZ;

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
                'APPROVED',
                'REJECTED',
                'ESCALATED',
                'CLOSED'
            )
        );
