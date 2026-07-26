ALTER TABLE investigation_cases
    DROP CONSTRAINT chk_investigation_cases_status;

ALTER TABLE investigation_cases
    ADD CONSTRAINT chk_investigation_cases_status
        CHECK (
            status IN (
                'NEW',
                'RUNNING',
                'REPORT_GENERATED',
                'OPEN',
                'INVESTIGATING',
                'AWAITING_REVIEW',
                'APPROVED',
                'REJECTED',
                'ESCALATED',
                'CLOSED'
            )
        );
