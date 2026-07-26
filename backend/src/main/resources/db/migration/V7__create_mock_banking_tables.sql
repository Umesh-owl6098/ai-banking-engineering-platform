-- gen_random_uuid() is available in PostgreSQL 13+ without an extension.
-- This project uses pgvector/pgvector:pg16 (see docker-compose.yml).

CREATE TABLE mock_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    full_name VARCHAR(200) NOT NULL,
    date_of_birth DATE NOT NULL,
    nationality VARCHAR(100),
    country_of_residence VARCHAR(100) NOT NULL,

    account_number VARCHAR(50) NOT NULL,
    account_status VARCHAR(50) NOT NULL,

    email VARCHAR(255),
    occupation VARCHAR(150),
    source_of_funds VARCHAR(200),

    kyc_status VARCHAR(50) NOT NULL,
    risk_rating VARCHAR(20) NOT NULL,
    pep_status VARCHAR(20) NOT NULL DEFAULT 'NONE',

    account_opened DATE NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_mock_customers_account_number
        UNIQUE (account_number),

    CONSTRAINT chk_mock_customers_kyc_status
        CHECK (kyc_status IN ('VERIFIED', 'PENDING', 'FAILED', 'EXPIRED')),

    CONSTRAINT chk_mock_customers_risk_rating
        CHECK (risk_rating IN ('LOW', 'MEDIUM', 'HIGH')),

    CONSTRAINT chk_mock_customers_account_status
        CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'FROZEN')),

    CONSTRAINT chk_mock_customers_pep_status
        CHECK (pep_status IN ('NONE', 'PEP', 'RCA'))
);

CREATE TABLE mock_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    customer_id UUID NOT NULL,

    transaction_reference VARCHAR(100) NOT NULL,
    transaction_date TIMESTAMPTZ NOT NULL,

    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    transaction_type VARCHAR(50) NOT NULL,
    transaction_status VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,

    counterparty_name VARCHAR(200),
    counterparty_bank VARCHAR(200),
    counterparty_country VARCHAR(100),

    origin_country VARCHAR(100),
    destination_country VARCHAR(100),

    description VARCHAR(500),

    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    risk_score NUMERIC(5, 2),
    risk_indicators TEXT[],

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_mock_transactions_reference
        UNIQUE (transaction_reference),

    CONSTRAINT fk_mock_transaction_customer
        FOREIGN KEY (customer_id)
            REFERENCES mock_customers(id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_mock_transactions_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_mock_transactions_risk_score
        CHECK (
            risk_score IS NULL
            OR (risk_score >= 0 AND risk_score <= 100)
        ),

    CONSTRAINT chk_mock_transactions_type
        CHECK (
            transaction_type IN (
                'TRANSFER',
                'WIRE',
                'ACH',
                'CARD_PAYMENT',
                'CASH_DEPOSIT',
                'CASH_WITHDRAWAL',
                'CRYPTO',
                'INTERNATIONAL_TRANSFER'
            )
        ),

    CONSTRAINT chk_mock_transactions_status
        CHECK (
            transaction_status IN (
                'PENDING',
                'COMPLETED',
                'FAILED',
                'REVERSED',
                'BLOCKED'
            )
        )
);

CREATE INDEX idx_mock_customers_kyc_status
    ON mock_customers(kyc_status);

CREATE INDEX idx_mock_customers_risk_rating
    ON mock_customers(risk_rating);

CREATE INDEX idx_mock_customers_account_status
    ON mock_customers(account_status);

CREATE INDEX idx_mock_customers_country
    ON mock_customers(country_of_residence);

CREATE INDEX idx_mock_customers_pep_status
    ON mock_customers(pep_status)
    WHERE pep_status <> 'NONE';

CREATE INDEX idx_mock_transactions_customer
    ON mock_transactions(customer_id);

CREATE INDEX idx_mock_transactions_date
    ON mock_transactions(transaction_date DESC);

CREATE INDEX idx_mock_transactions_flagged
    ON mock_transactions(transaction_date DESC, customer_id)
    WHERE flagged = TRUE;

CREATE INDEX idx_mock_transactions_origin_country
    ON mock_transactions(origin_country);

CREATE INDEX idx_mock_transactions_destination_country
    ON mock_transactions(destination_country)
    WHERE destination_country IS NOT NULL;

CREATE INDEX idx_mock_transactions_risk_score
    ON mock_transactions(risk_score DESC)
    WHERE risk_score IS NOT NULL;
