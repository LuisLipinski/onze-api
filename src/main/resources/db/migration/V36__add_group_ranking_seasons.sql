CREATE TABLE group_ranking_seasons (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    season_number INTEGER NOT NULL CHECK (season_number > 0),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    closed_snapshot TEXT,
    UNIQUE (group_id, season_number),
    CHECK ((ended_at IS NULL AND closed_snapshot IS NULL)
        OR (ended_at IS NOT NULL AND closed_snapshot IS NOT NULL))
);

CREATE UNIQUE INDEX ux_group_ranking_seasons_current
    ON group_ranking_seasons(group_id) WHERE ended_at IS NULL;

-- Preserve all existing history in the first season as well as in the general ranking.
INSERT INTO group_ranking_seasons (id, group_id, season_number, started_at)
SELECT gen_random_uuid(), id, 1, created_at FROM groups;
