ALTER TABLE group_members
    ADD COLUMN can_play_goalkeeper BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dominant_foot VARCHAR(16),
    ADD COLUMN technical_level SMALLINT,
    ADD CONSTRAINT ck_group_members_dominant_foot
        CHECK (dominant_foot IS NULL OR dominant_foot IN ('RIGHT', 'LEFT', 'BOTH')),
    ADD CONSTRAINT ck_group_members_technical_level
        CHECK (technical_level IS NULL OR technical_level BETWEEN 1 AND 5);

CREATE TABLE group_member_positions (
    group_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE CASCADE,
    position VARCHAR(32) NOT NULL,
    CONSTRAINT pk_group_member_positions PRIMARY KEY (group_member_id, position),
    CONSTRAINT ck_group_member_positions_position
        CHECK (position IN ('DEFENDER', 'MIDFIELDER', 'WINGER', 'STRIKER'))
);
