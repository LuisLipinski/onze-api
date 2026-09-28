ALTER TABLE football_matches
    ADD COLUMN periods_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN regulation_period_count INTEGER,
    ADD COLUMN regulation_period_minutes INTEGER,
    ADD COLUMN overtime_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN overtime_period_count INTEGER,
    ADD COLUMN overtime_period_minutes INTEGER,
    ADD COLUMN penalty_shootout_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE football_matches
    ADD CONSTRAINT ck_football_matches_period_configuration CHECK (
        (periods_enabled = FALSE
            AND regulation_period_count IS NULL
            AND regulation_period_minutes IS NULL
            AND overtime_enabled = FALSE
            AND overtime_period_count IS NULL
            AND overtime_period_minutes IS NULL
            AND penalty_shootout_enabled = FALSE)
        OR
        (periods_enabled = TRUE
            AND regulation_period_count BETWEEN 1 AND 4
            AND regulation_period_minutes BETWEEN 1 AND 180
            AND (
                (overtime_enabled = FALSE
                    AND overtime_period_count IS NULL
                    AND overtime_period_minutes IS NULL)
                OR
                (overtime_enabled = TRUE
                    AND overtime_period_count BETWEEN 1 AND 4
                    AND overtime_period_minutes BETWEEN 1 AND 180)
            ))
    );

ALTER TABLE match_series
    ADD COLUMN periods_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN regulation_period_count INTEGER,
    ADD COLUMN regulation_period_minutes INTEGER,
    ADD COLUMN overtime_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN overtime_period_count INTEGER,
    ADD COLUMN overtime_period_minutes INTEGER,
    ADD COLUMN penalty_shootout_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE match_series
    ADD CONSTRAINT ck_match_series_period_configuration CHECK (
        (periods_enabled = FALSE
            AND regulation_period_count IS NULL
            AND regulation_period_minutes IS NULL
            AND overtime_enabled = FALSE
            AND overtime_period_count IS NULL
            AND overtime_period_minutes IS NULL
            AND penalty_shootout_enabled = FALSE)
        OR
        (periods_enabled = TRUE
            AND regulation_period_count BETWEEN 1 AND 4
            AND regulation_period_minutes BETWEEN 1 AND 180
            AND (
                (overtime_enabled = FALSE
                    AND overtime_period_count IS NULL
                    AND overtime_period_minutes IS NULL)
                OR
                (overtime_enabled = TRUE
                    AND overtime_period_count BETWEEN 1 AND 4
                    AND overtime_period_minutes BETWEEN 1 AND 180)
            ))
    );

CREATE TABLE match_periods (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    period_type VARCHAR(16) NOT NULL,
    period_number INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    added_time_minutes INTEGER,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_match_periods_type_number UNIQUE (match_id, period_type, period_number),
    CONSTRAINT ck_match_periods_type CHECK (period_type IN ('REGULATION', 'OVERTIME')),
    CONSTRAINT ck_match_periods_number CHECK (period_number BETWEEN 1 AND 4),
    CONSTRAINT ck_match_periods_duration CHECK (duration_minutes BETWEEN 1 AND 180),
    CONSTRAINT ck_match_periods_added_time CHECK (added_time_minutes IS NULL OR added_time_minutes BETWEEN 0 AND 180),
    CONSTRAINT ck_match_periods_timestamps CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX ix_match_periods_match_started
    ON match_periods(match_id, started_at);

ALTER TABLE match_goal_events
    ADD COLUMN period_type VARCHAR(16),
    ADD COLUMN period_number INTEGER,
    ADD COLUMN period_elapsed_seconds BIGINT;

ALTER TABLE match_goal_events
    ADD CONSTRAINT ck_match_goal_events_period CHECK (
        (period_type IS NULL AND period_number IS NULL AND period_elapsed_seconds IS NULL)
        OR
        (period_type IN ('REGULATION', 'OVERTIME')
            AND period_number BETWEEN 1 AND 4
            AND period_elapsed_seconds >= 0)
    );

ALTER TABLE match_card_events
    ADD COLUMN period_type VARCHAR(16),
    ADD COLUMN period_number INTEGER,
    ADD COLUMN period_elapsed_seconds BIGINT;

ALTER TABLE match_card_events
    ADD CONSTRAINT ck_match_card_events_period CHECK (
        (period_type IS NULL AND period_number IS NULL AND period_elapsed_seconds IS NULL)
        OR
        (period_type IN ('REGULATION', 'OVERTIME')
            AND period_number BETWEEN 1 AND 4
            AND period_elapsed_seconds >= 0)
    );

CREATE TABLE match_penalty_shootouts (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL UNIQUE REFERENCES football_matches(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    winner_team_number INTEGER,
    started_at TIMESTAMP WITH TIME ZONE,
    decided_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_match_penalty_shootouts_status CHECK (
        status IN ('SETUP', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'COMPLETED')),
    CONSTRAINT ck_match_penalty_shootouts_winner CHECK (
        winner_team_number IS NULL OR winner_team_number IN (1, 2))
);

CREATE TABLE match_penalty_takers (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    team_number INTEGER NOT NULL,
    kick_order INTEGER NOT NULL,
    assignment_id UUID,
    participant_type VARCHAR(32),
    participant_id UUID,
    display_name VARCHAR(120) NOT NULL,
    CONSTRAINT uk_match_penalty_takers_order UNIQUE (match_id, team_number, kick_order),
    CONSTRAINT ck_match_penalty_takers_team CHECK (team_number IN (1, 2)),
    CONSTRAINT ck_match_penalty_takers_order CHECK (kick_order BETWEEN 1 AND 5),
    CONSTRAINT ck_match_penalty_takers_participant CHECK (
        (assignment_id IS NULL AND participant_type IS NULL AND participant_id IS NULL)
        OR
        (assignment_id IS NOT NULL AND participant_type IS NOT NULL AND participant_id IS NOT NULL)
    )
);

CREATE TABLE match_penalty_attempts (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    round_number INTEGER NOT NULL,
    team_number INTEGER NOT NULL,
    assignment_id UUID,
    participant_type VARCHAR(32),
    participant_id UUID,
    display_name VARCHAR(120) NOT NULL,
    scored BOOLEAN NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_match_penalty_attempts_sequence UNIQUE (match_id, sequence_number),
    CONSTRAINT ck_match_penalty_attempts_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_match_penalty_attempts_round CHECK (round_number >= 1),
    CONSTRAINT ck_match_penalty_attempts_team CHECK (team_number IN (1, 2)),
    CONSTRAINT ck_match_penalty_attempts_participant CHECK (
        (assignment_id IS NULL AND participant_type IS NULL AND participant_id IS NULL)
        OR
        (assignment_id IS NOT NULL AND participant_type IS NOT NULL AND participant_id IS NOT NULL)
    )
);

CREATE INDEX ix_match_penalty_attempts_match_sequence
    ON match_penalty_attempts(match_id, sequence_number);
