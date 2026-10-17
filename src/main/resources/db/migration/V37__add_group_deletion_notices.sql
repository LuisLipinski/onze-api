CREATE TABLE group_deletion_notices (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    group_name VARCHAR(120) NOT NULL,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    push_status VARCHAR(24) NOT NULL,
    push_attempts INTEGER NOT NULL DEFAULT 0,
    next_push_attempt_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_group_deletion_notice_recipient UNIQUE (group_id, recipient_user_id)
);

-- The group snapshot deliberately survives the deleted group and its matches.
CREATE INDEX idx_group_deletion_notice_unread
    ON group_deletion_notices (recipient_user_id, created_at)
    WHERE acknowledged_at IS NULL;
CREATE INDEX idx_group_deletion_notice_push
    ON group_deletion_notices (next_push_attempt_at, created_at)
    WHERE push_status = 'PENDING';
