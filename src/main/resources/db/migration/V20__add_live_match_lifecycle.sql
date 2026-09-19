ALTER TABLE football_matches
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ;

ALTER TABLE football_matches DROP CONSTRAINT IF EXISTS ck_football_matches_status;
ALTER TABLE football_matches
    ADD CONSTRAINT ck_football_matches_status
    CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'FINISHED', 'CANCELLED'));

ALTER TABLE football_matches
    ADD CONSTRAINT ck_football_matches_live_timestamps CHECK (
        (status = 'SCHEDULED' AND started_at IS NULL AND finished_at IS NULL)
        OR (status = 'IN_PROGRESS' AND started_at IS NOT NULL AND finished_at IS NULL)
        OR (status = 'FINISHED' AND started_at IS NOT NULL AND finished_at IS NOT NULL)
        OR status = 'CANCELLED'
    );
