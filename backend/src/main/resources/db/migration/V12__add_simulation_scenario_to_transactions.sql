ALTER TABLE mock_transactions
    ADD COLUMN simulation_scenario VARCHAR(50);

CREATE INDEX idx_mock_transactions_simulation_scenario
    ON mock_transactions(simulation_scenario)
    WHERE simulation_scenario IS NOT NULL;
