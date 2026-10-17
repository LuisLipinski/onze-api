CREATE SEQUENCE match_waitlist_order_seq;

ALTER TABLE match_attendances
    ADD COLUMN waitlist_joined_at TIMESTAMPTZ,
    ADD COLUMN waitlist_order BIGINT,
    ADD CONSTRAINT ck_match_waitlist_state CHECK (
        (status = 'WAITLISTED' AND waitlist_joined_at IS NOT NULL AND waitlist_order IS NOT NULL)
        OR (status <> 'WAITLISTED' AND waitlist_joined_at IS NULL AND waitlist_order IS NULL)
    ),
    ADD CONSTRAINT ck_match_waitlist_no_payment CHECK (
        status <> 'WAITLISTED' OR (payment_status IS NULL AND cash_amount_due = 0 AND cash_paid_amount = 0)
    );

CREATE UNIQUE INDEX uk_match_waitlist_order ON match_attendances(waitlist_order)
    WHERE status = 'WAITLISTED';
CREATE INDEX idx_match_waitlist_queue ON match_attendances(match_id, waitlist_order)
    WHERE status = 'WAITLISTED';

ALTER TABLE football_matches ADD COLUMN waitlist_guest_preference_key VARCHAR(36);
