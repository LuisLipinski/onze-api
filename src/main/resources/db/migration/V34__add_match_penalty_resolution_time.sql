ALTER TABLE match_foul_events
    ADD COLUMN resolution_elapsed_seconds BIGINT,
    ADD COLUMN resolution_period_type VARCHAR(16),
    ADD COLUMN resolution_period_number INTEGER,
    ADD COLUMN resolution_period_elapsed_seconds BIGINT;
