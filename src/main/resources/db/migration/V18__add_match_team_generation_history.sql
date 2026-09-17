CREATE TABLE match_team_generation_history (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    normalized_signature VARCHAR(4096) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_match_team_generation_history_signature UNIQUE (match_id, normalized_signature)
);

CREATE INDEX idx_match_team_generation_history_match_created
    ON match_team_generation_history(match_id, created_at);
