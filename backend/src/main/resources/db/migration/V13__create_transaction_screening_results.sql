CREATE TABLE transaction_screening_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transaction_id UUID NOT NULL,

    status VARCHAR(50) NOT NULL,
    screening_score NUMERIC(5, 2) NOT NULL DEFAULT 0,
    triggered_rules TEXT[],
    reason VARCHAR(500),
    screened_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_screening_transaction
        UNIQUE (transaction_id),

    CONSTRAINT fk_screening_transaction
        FOREIGN KEY (transaction_id)
            REFERENCES mock_transactions(id)
            ON DELETE CASCADE,

    CONSTRAINT chk_screening_status
        CHECK (
            status IN (
                'PROCESSING',
                'CLEARED',
                'SUSPICIOUS',
                'CRITICAL',
                'SCREENING_FAILED'
            )
        ),

    CONSTRAINT chk_screening_score
        CHECK (
            screening_score >= 0
            AND screening_score <= 100
        )
);

CREATE INDEX idx_screening_results_status
    ON transaction_screening_results(status);

CREATE INDEX idx_screening_results_screened_at
    ON transaction_screening_results(screened_at DESC)
    WHERE screened_at IS NOT NULL;
