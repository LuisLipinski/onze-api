ALTER TABLE football_matches
    ADD COLUMN goalkeeper_pays BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE match_series
    ADD COLUMN goalkeeper_pays BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE match_attendances
    ADD COLUMN is_goalkeeper BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE match_rental_goalkeepers (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_match_rental_goalkeepers_display_name
        CHECK (CHAR_LENGTH(BTRIM(display_name)) BETWEEN 1 AND 120)
);

CREATE INDEX idx_match_rental_goalkeepers_match_id
    ON match_rental_goalkeepers(match_id, created_at);
