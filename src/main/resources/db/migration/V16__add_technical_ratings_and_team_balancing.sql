ALTER TABLE group_members
    ADD COLUMN technical_profile_updated_at TIMESTAMPTZ;

CREATE TABLE group_member_skill_ratings (
    id UUID PRIMARY KEY,
    group_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE CASCADE,
    skill VARCHAR(48) NOT NULL,
    rating INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_group_member_skill_ratings_member_skill UNIQUE (group_member_id, skill),
    CONSTRAINT ck_group_member_skill_ratings_rating CHECK (rating BETWEEN 1 AND 10)
);

CREATE INDEX idx_group_member_skill_ratings_member
    ON group_member_skill_ratings(group_member_id);

-- The legacy technical_level column is intentionally retained for old clients.
-- It is not converted into skills because doing so would invent evaluation data.

ALTER TABLE football_matches
    ADD COLUMN modality VARCHAR(16) NOT NULL DEFAULT 'FUT7',
    ADD COLUMN minimum_players INTEGER;

UPDATE football_matches
SET minimum_players = LEAST(
    max_players,
    CASE modality
        WHEN 'FIELD' THEN 11 * COALESCE(team_count, 1)
        WHEN 'FUTSAL' THEN 5 * COALESCE(team_count, 1)
        ELSE 7 * COALESCE(team_count, 1)
    END
);

ALTER TABLE football_matches
    ALTER COLUMN minimum_players SET NOT NULL,
    ADD CONSTRAINT ck_football_matches_modality
        CHECK (modality IN ('FIELD', 'FUT7', 'FUTSAL')),
    ADD CONSTRAINT ck_football_matches_minimum_players
        CHECK (minimum_players > 0 AND minimum_players <= max_players);

ALTER TABLE match_series
    ADD COLUMN modality VARCHAR(16) NOT NULL DEFAULT 'FUT7',
    ADD COLUMN minimum_players INTEGER;

UPDATE match_series
SET minimum_players = LEAST(
    max_players,
    CASE modality
        WHEN 'FIELD' THEN 11 * COALESCE(team_count, 1)
        WHEN 'FUTSAL' THEN 5 * COALESCE(team_count, 1)
        ELSE 7 * COALESCE(team_count, 1)
    END
);

ALTER TABLE match_series
    ALTER COLUMN minimum_players SET NOT NULL,
    ADD CONSTRAINT ck_match_series_modality
        CHECK (modality IN ('FIELD', 'FUT7', 'FUTSAL')),
    ADD CONSTRAINT ck_match_series_minimum_players
        CHECK (minimum_players > 0 AND minimum_players <= max_players);

CREATE TABLE match_guests (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    primary_position VARCHAR(32) NOT NULL,
    secondary_position VARCHAR(32),
    technical_profile_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_match_guests_display_name CHECK (CHAR_LENGTH(BTRIM(display_name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_match_guests_distinct_positions CHECK (
        secondary_position IS NULL OR secondary_position <> primary_position
    )
);

CREATE INDEX idx_match_guests_match ON match_guests(match_id, created_at);

CREATE TABLE match_guest_skill_ratings (
    id UUID PRIMARY KEY,
    guest_id UUID NOT NULL REFERENCES match_guests(id) ON DELETE CASCADE,
    skill VARCHAR(48) NOT NULL,
    rating INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_match_guest_skill_ratings_guest_skill UNIQUE (guest_id, skill),
    CONSTRAINT ck_match_guest_skill_ratings_rating CHECK (rating BETWEEN 1 AND 10)
);

CREATE INDEX idx_match_guest_skill_ratings_guest
    ON match_guest_skill_ratings(guest_id);

CREATE TABLE match_team_assignments (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    team_number INTEGER NOT NULL,
    participant_type VARCHAR(32) NOT NULL,
    participant_id UUID NOT NULL,
    assigned_role VARCHAR(48) NOT NULL,
    assignment_reason VARCHAR(48) NOT NULL,
    position_origin VARCHAR(24) NOT NULL,
    manually_changed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_match_team_assignments_participant UNIQUE (
        match_id, participant_type, participant_id
    ),
    CONSTRAINT ck_match_team_assignments_team CHECK (team_number >= 1),
    CONSTRAINT ck_match_team_assignments_participant_type CHECK (
        participant_type IN ('MEMBER', 'GUEST', 'RENTAL_GOALKEEPER')
    )
);

CREATE INDEX idx_match_team_assignments_match_team
    ON match_team_assignments(match_id, team_number);
