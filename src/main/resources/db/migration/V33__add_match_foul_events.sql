CREATE TABLE match_foul_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    offender_side_number INTEGER NOT NULL CHECK (offender_side_number >= 1),
    offender_assignment_id UUID NOT NULL,
    offender_participant_type VARCHAR(32) NOT NULL,
    offender_participant_id UUID NOT NULL,
    offender_display_name VARCHAR(120) NOT NULL,
    victim_side_number INTEGER,
    victim_assignment_id UUID,
    victim_participant_type VARCHAR(32),
    victim_participant_id UUID,
    victim_display_name VARCHAR(120),
    penalty BOOLEAN NOT NULL DEFAULT FALSE,
    taker_assignment_id UUID,
    taker_participant_type VARCHAR(32),
    taker_participant_id UUID,
    taker_display_name VARCHAR(120),
    scored BOOLEAN,
    goal_event_id UUID UNIQUE REFERENCES match_goal_events(id),
    shot_event_id UUID UNIQUE REFERENCES match_shot_events(id),
    elapsed_seconds BIGINT NOT NULL,
    period_type VARCHAR(16),
    period_number INTEGER,
    period_elapsed_seconds BIGINT,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_match_foul_victim CHECK (
        (victim_side_number IS NULL AND victim_assignment_id IS NULL
            AND victim_participant_type IS NULL AND victim_participant_id IS NULL
            AND victim_display_name IS NULL)
        OR (victim_side_number IS NOT NULL AND victim_assignment_id IS NOT NULL
            AND victim_participant_type IS NOT NULL AND victim_participant_id IS NOT NULL
            AND victim_display_name IS NOT NULL AND victim_side_number <> offender_side_number)
    ),
    CONSTRAINT ck_match_foul_penalty CHECK (
        (penalty AND victim_assignment_id IS NOT NULL)
        OR (NOT penalty AND taker_assignment_id IS NULL AND scored IS NULL
            AND goal_event_id IS NULL AND shot_event_id IS NULL AND resolved_at IS NULL)
    ),
    CONSTRAINT ck_match_foul_resolution CHECK (
        (scored IS NULL AND taker_assignment_id IS NULL AND taker_participant_type IS NULL
            AND taker_participant_id IS NULL AND taker_display_name IS NULL
            AND goal_event_id IS NULL AND shot_event_id IS NULL AND resolved_at IS NULL)
        OR (scored IS NOT NULL AND taker_assignment_id IS NOT NULL
            AND taker_participant_type IS NOT NULL AND taker_participant_id IS NOT NULL
            AND taker_display_name IS NOT NULL AND resolved_at IS NOT NULL
            AND (scored = (goal_event_id IS NOT NULL))
            AND (scored OR shot_event_id IS NOT NULL))
    )
);

CREATE INDEX ix_match_foul_events_match_time
    ON match_foul_events(match_id, elapsed_seconds DESC, created_at DESC);
