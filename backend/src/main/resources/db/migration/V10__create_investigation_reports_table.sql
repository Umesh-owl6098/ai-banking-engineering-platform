CREATE TABLE investigation_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    case_id UUID NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETE',

    structured_json JSONB NOT NULL,
    raw_llm_response TEXT,

    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_investigation_report_case
        FOREIGN KEY (case_id)
            REFERENCES investigation_cases(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_investigation_reports_status
        CHECK (
            status IN (
                'COMPLETE',
                'FAILED'
            )
        )
);

CREATE INDEX idx_investigation_reports_case_generated
    ON investigation_reports(case_id, generated_at DESC);
