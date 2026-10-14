ALTER TABLE match_goal_events
    ADD COLUMN own_goal BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN shot_recorded BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE match_goal_events
    ADD CONSTRAINT ck_match_goal_events_own_goal CHECK (
        NOT own_goal OR (NOT penalty AND assist_assignment_id IS NULL AND NOT shot_recorded)
    );

CREATE TABLE match_shot_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    side_number INTEGER NOT NULL CHECK (side_number >= 1),
    player_assignment_id UUID NOT NULL,
    player_participant_type VARCHAR(32) NOT NULL,
    player_participant_id UUID NOT NULL,
    player_display_name VARCHAR(120) NOT NULL,
    on_target BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_match_shot_events_match_side_time
    ON match_shot_events(match_id, side_number, created_at DESC);
