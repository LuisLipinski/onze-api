CREATE TABLE live_match_team_statistics (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    side_number INTEGER NOT NULL,
    shots INTEGER NOT NULL DEFAULT 0,
    shots_on_target INTEGER NOT NULL DEFAULT 0,
    corners INTEGER NOT NULL DEFAULT 0,
    fouls INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_live_match_team_statistics_match_side UNIQUE (match_id, side_number),
    CONSTRAINT ck_live_match_team_statistics_side CHECK (side_number >= 1),
    CONSTRAINT ck_live_match_team_statistics_counts CHECK (
        shots >= 0 AND shots_on_target >= 0 AND shots_on_target <= shots
        AND corners >= 0 AND fouls >= 0)
);

CREATE INDEX ix_live_match_team_statistics_match_id ON live_match_team_statistics(match_id);
