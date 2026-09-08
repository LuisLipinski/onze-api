ALTER TABLE match_attendances
    ADD COLUMN credit_applied_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cash_amount_due NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cash_paid_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN credit_consumed_at TIMESTAMPTZ,
    ADD COLUMN credit_returned_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_match_attendances_credit_applied_amount
        CHECK (credit_applied_amount >= 0),
    ADD CONSTRAINT ck_match_attendances_cash_amount_due
        CHECK (cash_amount_due >= 0),
    ADD CONSTRAINT ck_match_attendances_cash_paid_amount
        CHECK (cash_paid_amount >= 0);

UPDATE match_attendances attendance
SET cash_amount_due = match.payment_amount,
    cash_paid_amount = CASE
        WHEN attendance.payment_status = 'PAID' THEN match.payment_amount
        ELSE 0
    END
FROM football_matches match
WHERE attendance.match_id = match.id
  AND attendance.payment_status IS NOT NULL
  AND match.payment_amount IS NOT NULL;

CREATE TABLE group_player_credits (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    balance NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_group_player_credits_group_user UNIQUE (group_id, user_id),
    CONSTRAINT ck_group_player_credits_balance CHECK (balance >= 0)
);

CREATE INDEX idx_group_player_credits_group
    ON group_player_credits(group_id);

INSERT INTO group_player_credits (id, group_id, user_id, balance)
SELECT
    gen_random_uuid(),
    match.group_id,
    attendance.user_id,
    SUM(match.payment_amount)
FROM match_attendances attendance
JOIN football_matches match ON match.id = attendance.match_id
WHERE attendance.payment_settlement_status = 'CREDITED'
  AND match.payment_amount IS NOT NULL
GROUP BY match.group_id, attendance.user_id;
