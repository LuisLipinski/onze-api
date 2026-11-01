ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ NULL;

CREATE TABLE auth_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    family_expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    legacy_grant_hash VARCHAR(64) NULL UNIQUE
);

CREATE INDEX idx_auth_refresh_tokens_user ON auth_refresh_tokens (user_id);
CREATE INDEX idx_auth_refresh_tokens_family ON auth_refresh_tokens (family_id);
