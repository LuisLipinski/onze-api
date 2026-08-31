ALTER TABLE groups
    ADD COLUMN default_payment_amount NUMERIC(10, 2),
    ADD COLUMN default_pix_key VARCHAR(255),
    ADD CONSTRAINT ck_groups_default_payment_amount
        CHECK (default_payment_amount IS NULL OR default_payment_amount >= 0.01);

ALTER TABLE match_series
    ADD COLUMN payment_amount NUMERIC(10, 2),
    ADD COLUMN pix_key VARCHAR(255),
    ADD CONSTRAINT ck_match_series_payment_amount
        CHECK (payment_amount IS NULL OR payment_amount >= 0.01);

ALTER TABLE football_matches
    ADD COLUMN payment_amount NUMERIC(10, 2),
    ADD COLUMN pix_key VARCHAR(255),
    ADD CONSTRAINT ck_football_matches_payment_amount
        CHECK (payment_amount IS NULL OR payment_amount >= 0.01);

ALTER TABLE match_attendances
    ADD COLUMN payment_status VARCHAR(24),
    ADD COLUMN payment_reported_at TIMESTAMPTZ,
    ADD COLUMN payment_confirmed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_match_attendances_payment_status
        CHECK (payment_status IS NULL OR payment_status IN ('PENDING', 'REPORTED', 'PAID'));

ALTER TABLE match_notification_jobs
    DROP CONSTRAINT uk_match_notification_job,
    ADD COLUMN recipient_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN deduplication_key VARCHAR(255);

UPDATE match_notification_jobs
SET deduplication_key = match_id::text || ':' || notification_type;

ALTER TABLE match_notification_jobs
    ALTER COLUMN deduplication_key SET NOT NULL,
    ALTER COLUMN notification_type TYPE VARCHAR(48),
    ADD CONSTRAINT uk_match_notification_job_deduplication UNIQUE (deduplication_key);

CREATE INDEX idx_match_notification_jobs_recipient
    ON match_notification_jobs(recipient_user_id)
    WHERE recipient_user_id IS NOT NULL;
