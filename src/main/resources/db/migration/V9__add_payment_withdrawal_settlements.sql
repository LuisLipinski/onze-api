ALTER TABLE match_attendances
    DROP CONSTRAINT ck_match_attendances_payment_status,
    ADD CONSTRAINT ck_match_attendances_payment_status
        CHECK (payment_status IS NULL OR payment_status IN ('PENDING', 'REPORTED', 'PAID', 'CANCELLED')),
    ADD COLUMN payment_settlement_status VARCHAR(32),
    ADD COLUMN payment_settlement_requested_at TIMESTAMPTZ,
    ADD COLUMN payment_settlement_resolved_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_match_attendances_payment_settlement_status
        CHECK (
            payment_settlement_status IS NULL
            OR payment_settlement_status IN (
                'REVIEW_REQUIRED',
                'PENDING',
                'NOT_RECEIVED',
                'REFUNDED',
                'CREDITED',
                'RETAINED'
            )
        );

UPDATE match_attendances
SET payment_status = 'CANCELLED'
WHERE status = 'NOT_GOING'
  AND payment_status = 'PENDING';

UPDATE match_attendances
SET payment_settlement_status = 'REVIEW_REQUIRED',
    payment_settlement_requested_at = updated_at
WHERE status = 'NOT_GOING'
  AND payment_status = 'REPORTED';

UPDATE match_attendances
SET payment_settlement_status = 'PENDING',
    payment_settlement_requested_at = updated_at
WHERE status = 'NOT_GOING'
  AND payment_status = 'PAID';
