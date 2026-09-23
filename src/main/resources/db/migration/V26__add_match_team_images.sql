CREATE TABLE match_team_images (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    team_number INTEGER NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_match_team_images_team_number CHECK (team_number >= 1),
    CONSTRAINT uk_match_team_images_match_team UNIQUE (match_id, team_number)
);

CREATE INDEX ix_match_team_images_match_id ON match_team_images(match_id);
