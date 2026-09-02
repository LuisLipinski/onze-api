ALTER TABLE football_matches
    ADD COLUMN signup_deadline TIMESTAMPTZ,
    ADD COLUMN payment_deadline TIMESTAMPTZ;

UPDATE football_matches
SET signup_deadline = starts_at,
    payment_deadline = CASE
        WHEN payment_amount IS NOT NULL THEN starts_at
        ELSE NULL
    END;

ALTER TABLE football_matches
    ALTER COLUMN signup_deadline SET NOT NULL,
    ADD CONSTRAINT ck_football_matches_signup_deadline
        CHECK (signup_deadline <= starts_at),
    ADD CONSTRAINT ck_football_matches_payment_deadline
        CHECK (
            (payment_amount IS NULL AND payment_deadline IS NULL)
            OR (
                payment_amount IS NOT NULL
                AND payment_deadline IS NOT NULL
                AND signup_deadline <= payment_deadline
                AND payment_deadline <= starts_at
            )
        );

CREATE INDEX idx_football_matches_signup_deadline
    ON football_matches(signup_deadline)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_football_matches_payment_deadline
    ON football_matches(payment_deadline)
    WHERE status = 'SCHEDULED' AND payment_deadline IS NOT NULL;

ALTER TABLE match_attendances
    ADD COLUMN payment_deadline_removed_at TIMESTAMPTZ;
