CREATE TABLE group_invites (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    code VARCHAR(12) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_group_invites_group UNIQUE (group_id),
    CONSTRAINT uk_group_invites_code UNIQUE (code)
);

CREATE INDEX idx_group_invites_code ON group_invites(code);
