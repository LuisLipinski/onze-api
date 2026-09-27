ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN login_attempt_window_started_at TIMESTAMPTZ NULL,
    ADD COLUMN login_blocked_until TIMESTAMPTZ NULL;

ALTER TABLE users
    ADD CONSTRAINT ck_users_failed_login_attempts_non_negative
        CHECK (failed_login_attempts >= 0);
