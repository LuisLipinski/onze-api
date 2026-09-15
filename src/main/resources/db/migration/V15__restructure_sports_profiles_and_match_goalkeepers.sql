ALTER TABLE group_members
    ADD COLUMN primary_position VARCHAR(32),
    ADD COLUMN secondary_position VARCHAR(32);

WITH mapped_positions AS (
    SELECT
        group_member_id,
        CASE position
            WHEN 'DEFENDER' THEN 'DEFENDER'
            WHEN 'MIDFIELDER' THEN 'MIDFIELDER'
            WHEN 'WINGER' THEN 'ATTACKER'
            WHEN 'STRIKER' THEN 'ATTACKER'
        END AS mapped_position,
        MIN(CASE position
            WHEN 'DEFENDER' THEN 1
            WHEN 'MIDFIELDER' THEN 2
            WHEN 'WINGER' THEN 3
            WHEN 'STRIKER' THEN 4
        END) AS migration_order
    FROM group_member_positions
    GROUP BY group_member_id,
        CASE position
            WHEN 'DEFENDER' THEN 'DEFENDER'
            WHEN 'MIDFIELDER' THEN 'MIDFIELDER'
            WHEN 'WINGER' THEN 'ATTACKER'
            WHEN 'STRIKER' THEN 'ATTACKER'
        END
), ranked_positions AS (
    SELECT
        group_member_id,
        mapped_position,
        ROW_NUMBER() OVER (
            PARTITION BY group_member_id
            ORDER BY migration_order
        ) AS position_number
    FROM mapped_positions
), migrated_profiles AS (
    SELECT
        group_member_id,
        MAX(mapped_position) FILTER (WHERE position_number = 1) AS primary_position,
        MAX(mapped_position) FILTER (WHERE position_number = 2) AS secondary_position
    FROM ranked_positions
    GROUP BY group_member_id
)
UPDATE group_members AS member
SET
    primary_position = profile.primary_position,
    secondary_position = profile.secondary_position
FROM migrated_profiles AS profile
WHERE profile.group_member_id = member.id;

ALTER TABLE group_members
    ADD CONSTRAINT ck_group_members_primary_position
        CHECK (primary_position IS NULL OR primary_position IN (
            'GOALKEEPER',
            'DEFENDER', 'RIGHT_DEFENDER', 'LEFT_DEFENDER', 'CENTER_DEFENDER',
            'RIGHT_BACK', 'LEFT_BACK',
            'DEFENSIVE_MIDFIELDER', 'MIDFIELDER', 'RIGHT_MIDFIELDER',
            'LEFT_MIDFIELDER', 'CENTRAL_MIDFIELDER', 'PLAYMAKER',
            'ATTACKER', 'RIGHT_WINGER', 'LEFT_WINGER', 'CENTER_FORWARD'
        )),
    ADD CONSTRAINT ck_group_members_secondary_position
        CHECK (secondary_position IS NULL OR secondary_position IN (
            'GOALKEEPER',
            'DEFENDER', 'RIGHT_DEFENDER', 'LEFT_DEFENDER', 'CENTER_DEFENDER',
            'RIGHT_BACK', 'LEFT_BACK',
            'DEFENSIVE_MIDFIELDER', 'MIDFIELDER', 'RIGHT_MIDFIELDER',
            'LEFT_MIDFIELDER', 'CENTRAL_MIDFIELDER', 'PLAYMAKER',
            'ATTACKER', 'RIGHT_WINGER', 'LEFT_WINGER', 'CENTER_FORWARD'
        )),
    ADD CONSTRAINT ck_group_members_secondary_requires_primary
        CHECK (secondary_position IS NULL OR primary_position IS NOT NULL),
    ADD CONSTRAINT ck_group_members_distinct_positions
        CHECK (secondary_position IS NULL OR secondary_position <> primary_position),
    ADD CONSTRAINT ck_group_members_goalkeeper_preference
        CHECK (
            NOT can_play_goalkeeper
            OR (
                primary_position IS DISTINCT FROM 'GOALKEEPER'
                AND secondary_position IS DISTINCT FROM 'GOALKEEPER'
            )
        );

COMMENT ON TABLE group_member_positions IS
    'Legacy sports-profile positions retained for data compatibility after V15; new writes use primary_position and secondary_position.';

ALTER TABLE football_matches
    ADD COLUMN match_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN team_count INTEGER,
    ADD COLUMN required_goalkeepers INTEGER NOT NULL DEFAULT 2;

UPDATE football_matches
SET team_count = 2
WHERE match_type = 'INTERNAL';

ALTER TABLE football_matches
    ADD CONSTRAINT ck_football_matches_format
        CHECK (
            (
                match_type = 'INTERNAL'
                AND team_count >= 2
                AND required_goalkeepers >= team_count
            )
            OR (
                match_type = 'VERSUS_EXTERNAL'
                AND team_count IS NULL
                AND required_goalkeepers >= 1
            )
        );

ALTER TABLE match_series
    ADD COLUMN match_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN team_count INTEGER,
    ADD COLUMN required_goalkeepers INTEGER NOT NULL DEFAULT 2;

UPDATE match_series
SET team_count = 2
WHERE match_type = 'INTERNAL';

ALTER TABLE match_series
    ADD CONSTRAINT ck_match_series_format
        CHECK (
            (
                match_type = 'INTERNAL'
                AND team_count >= 2
                AND required_goalkeepers >= team_count
            )
            OR (
                match_type = 'VERSUS_EXTERNAL'
                AND team_count IS NULL
                AND required_goalkeepers >= 1
            )
        );
