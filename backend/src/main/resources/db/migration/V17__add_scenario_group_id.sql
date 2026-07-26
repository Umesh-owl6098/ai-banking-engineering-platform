ALTER TABLE mock_transactions
    ADD COLUMN scenario_group_id VARCHAR(100);

CREATE INDEX idx_mock_transactions_scenario_group_id
    ON mock_transactions (scenario_group_id)
    WHERE scenario_group_id IS NOT NULL;

ALTER TABLE investigation_cases
    ADD COLUMN scenario_group_id VARCHAR(100);

CREATE INDEX idx_investigation_cases_scenario_group_id
    ON investigation_cases (scenario_group_id)
    WHERE scenario_group_id IS NOT NULL;
