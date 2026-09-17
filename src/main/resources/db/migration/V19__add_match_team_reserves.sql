CREATE TABLE match_team_reserves (
    assignment_id UUID PRIMARY KEY REFERENCES match_team_assignments(id) ON DELETE CASCADE,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_match_team_reserves_match
    ON match_team_reserves(match_id);
