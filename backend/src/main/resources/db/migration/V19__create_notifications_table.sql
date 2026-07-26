CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    related_investigation_id UUID,
    related_transaction_id UUID,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_notifications_investigation
        FOREIGN KEY (related_investigation_id)
            REFERENCES investigation_cases(id)
            ON DELETE SET NULL,
    CONSTRAINT fk_notifications_transaction
        FOREIGN KEY (related_transaction_id)
            REFERENCES mock_transactions(id)
            ON DELETE SET NULL,
    CONSTRAINT chk_notifications_type
        CHECK (
            type IN (
                'CRITICAL_INVESTIGATION_CREATED',
                'INVESTIGATION_ASSIGNED',
                'INVESTIGATION_CLAIMED',
                'INVESTIGATION_REASSIGNED',
                'REPORT_GENERATED',
                'INVESTIGATION_ESCALATED',
                'AI_EXECUTION_FAILURE',
                'INVESTIGATION_WAITING_TOO_LONG',
                'OPENAI_FALLBACK_MODE'
            )
        ),
    CONSTRAINT chk_notifications_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

CREATE INDEX idx_notifications_user_read_created
    ON notifications(user_id, read, created_at DESC);

CREATE INDEX idx_notifications_investigation_type
    ON notifications(related_investigation_id, type, created_at DESC);
