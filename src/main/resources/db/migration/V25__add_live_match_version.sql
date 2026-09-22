ALTER TABLE football_matches
    ADD COLUMN live_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_football_matches_status_group
    ON football_matches(status, group_id);
