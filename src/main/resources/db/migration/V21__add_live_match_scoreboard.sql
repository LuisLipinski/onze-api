CREATE TABLE live_match_scores (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    side_number INTEGER NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_live_match_scores_side_number CHECK (side_number >= 1),
    CONSTRAINT ck_live_match_scores_score CHECK (score >= 0),
    CONSTRAINT uk_live_match_scores_match_side UNIQUE (match_id, side_number)
);

CREATE INDEX ix_live_match_scores_match_id ON live_match_scores(match_id);
