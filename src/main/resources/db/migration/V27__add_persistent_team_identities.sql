ALTER TABLE match_team_images
    ADD COLUMN team_name VARCHAR(80);

UPDATE match_team_images
SET team_name = 'Time ' || team_number
WHERE team_name IS NULL;

ALTER TABLE match_team_images
    ALTER COLUMN team_name SET NOT NULL;

ALTER TABLE match_team_images
    ALTER COLUMN image_url DROP NOT NULL;

CREATE TABLE group_team_identities (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    match_type VARCHAR(32) NOT NULL,
    team_number INTEGER NOT NULL,
    team_name VARCHAR(80) NOT NULL,
    image_url VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_group_team_identities_team_number CHECK (team_number >= 1),
    CONSTRAINT ck_group_team_identities_match_type
        CHECK (match_type IN ('INTERNAL', 'VERSUS_EXTERNAL')),
    CONSTRAINT uk_group_team_identities_group_type_team
        UNIQUE (group_id, match_type, team_number)
);

CREATE INDEX ix_group_team_identities_group_type
    ON group_team_identities(group_id, match_type);

INSERT INTO group_team_identities (
    id,
    group_id,
    match_type,
    team_number,
    team_name,
    image_url,
    created_at,
    updated_at
)
SELECT
    id,
    group_id,
    match_type,
    team_number,
    team_name,
    image_url,
    NOW(),
    NOW()
FROM (
    SELECT
        mti.id,
        fm.group_id,
        fm.match_type,
        mti.team_number,
        mti.team_name,
        mti.image_url,
        ROW_NUMBER() OVER (
            PARTITION BY fm.group_id, fm.match_type, mti.team_number
            ORDER BY COALESCE(fm.started_at, fm.starts_at) DESC, mti.updated_at DESC
        ) AS row_num
    FROM match_team_images mti
    JOIN football_matches fm ON fm.id = mti.match_id
    WHERE mti.image_url IS NOT NULL
) latest
WHERE row_num = 1;
