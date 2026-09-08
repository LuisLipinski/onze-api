ALTER TABLE match_attendances
    ADD COLUMN replacement_required_at TIMESTAMPTZ,
    ADD COLUMN replacement_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN replacement_filled_at TIMESTAMPTZ,
    ADD COLUMN added_as_replacement_at TIMESTAMPTZ,
    ADD COLUMN replacement_for_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_match_attendances_replacement_filled
        CHECK (
            (replacement_user_id IS NULL AND replacement_filled_at IS NULL)
            OR (replacement_user_id IS NOT NULL AND replacement_filled_at IS NOT NULL)
        );

CREATE INDEX idx_match_attendances_open_replacement
    ON match_attendances(match_id, replacement_required_at)
    WHERE replacement_required_at IS NOT NULL AND replacement_filled_at IS NULL;
