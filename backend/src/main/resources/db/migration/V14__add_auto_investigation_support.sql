ALTER TABLE investigation_cases
    DROP CONSTRAINT chk_investigation_cases_status;

ALTER TABLE investigation_cases
    ADD CONSTRAINT chk_investigation_cases_status
        CHECK (
            status IN (
                'NEW',
                'OPEN',
                'INVESTIGATING',
                'AWAITING_REVIEW',
                'APPROVED',
                'REJECTED',
                'ESCALATED',
                'CLOSED'
            )
        );

ALTER TABLE investigation_cases
    ADD COLUMN auto_created BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE investigation_cases
    ADD COLUMN screening_status VARCHAR(50);

ALTER TABLE investigation_cases
    ADD COLUMN screening_reason VARCHAR(500);

ALTER TABLE investigation_cases
    ADD COLUMN screening_triggered_rules TEXT[];

CREATE INDEX idx_investigation_cases_auto_created
    ON investigation_cases(auto_created, created_at DESC)
    WHERE auto_created = TRUE;

INSERT INTO projects (
    id,
    name,
    description,
    status,
    created_at,
    updated_at
)
VALUES (
    '8c0c0dee-dd8e-4419-bef3-a2e93c10a726',
    'Financial Crime Monitoring',
    'Default project for automated simulator investigations',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
