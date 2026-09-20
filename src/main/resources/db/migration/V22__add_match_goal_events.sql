CREATE TABLE match_goal_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    side_number INTEGER NOT NULL,
    scorer_assignment_id UUID NOT NULL,
    scorer_participant_type VARCHAR(32) NOT NULL,
    scorer_participant_id UUID NOT NULL,
    assist_assignment_id UUID,
    assist_participant_type VARCHAR(32),
    assist_participant_id UUID,
    penalty BOOLEAN NOT NULL DEFAULT FALSE,
    elapsed_seconds BIGINT NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_match_goal_events_side_number CHECK (side_number >= 1),
    CONSTRAINT ck_match_goal_events_elapsed_seconds CHECK (elapsed_seconds >= 0),
    CONSTRAINT ck_match_goal_events_assist_complete CHECK (
        (assist_assignment_id IS NULL AND assist_participant_type IS NULL AND assist_participant_id IS NULL)
        OR
        (assist_assignment_id IS NOT NULL AND assist_participant_type IS NOT NULL AND assist_participant_id IS NOT NULL)
    ),
    CONSTRAINT ck_match_goal_events_penalty_without_assist CHECK (
        NOT penalty OR assist_assignment_id IS NULL
    )
);

CREATE INDEX ix_match_goal_events_match_time
    ON match_goal_events(match_id, elapsed_seconds, created_at);
