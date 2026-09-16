CREATE TABLE match_team_generation_history (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    group_id UUID NOT NULL,
    generation_number INTEGER NOT NULL,
    division_signature TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_match_team_generation_history_number UNIQUE (match_id, generation_number),
    CONSTRAINT ck_match_team_generation_history_number CHECK (generation_number > 0)
);

CREATE INDEX idx_match_team_generation_history_group_created
    ON match_team_generation_history(group_id, created_at DESC);

CREATE INDEX idx_match_team_generation_history_match_generation
    ON match_team_generation_history(match_id, generation_number DESC);
