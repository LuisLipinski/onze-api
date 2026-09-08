CREATE TABLE match_series (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(id),
    time_zone VARCHAR(64) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    max_players INTEGER NOT NULL,
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_match_series_max_players CHECK (max_players BETWEEN 2 AND 100)
);

CREATE INDEX idx_match_series_group_id ON match_series(group_id);

CREATE TABLE football_matches (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    series_id UUID REFERENCES match_series(id) ON DELETE CASCADE,
    occurrence_number INTEGER,
    starts_at TIMESTAMPTZ NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    max_players INTEGER NOT NULL,
    notes VARCHAR(1000),
    status VARCHAR(24) NOT NULL,
    attendance_opens_at TIMESTAMPTZ NOT NULL,
    attendance_opened_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_football_matches_max_players CHECK (max_players BETWEEN 2 AND 100),
    CONSTRAINT ck_football_matches_occurrence CHECK (
        (series_id IS NULL AND occurrence_number IS NULL)
        OR (series_id IS NOT NULL AND occurrence_number IS NOT NULL AND occurrence_number > 0)
    ),
    CONSTRAINT uk_football_matches_series_occurrence UNIQUE (series_id, occurrence_number)
);

CREATE INDEX idx_football_matches_group_starts_at
    ON football_matches(group_id, starts_at);
CREATE INDEX idx_football_matches_opening
    ON football_matches(attendance_opens_at)
    WHERE status = 'SCHEDULED' AND attendance_opened_at IS NULL;

CREATE TABLE match_attendances (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_match_attendances_match_user UNIQUE (match_id, user_id)
);

CREATE INDEX idx_match_attendances_match_id ON match_attendances(match_id);

CREATE TABLE push_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expo_push_token VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_push_devices_expo_token UNIQUE (expo_push_token)
);

CREATE INDEX idx_push_devices_user_active ON push_devices(user_id, active);

CREATE TABLE match_notification_jobs (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    notification_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT uk_match_notification_job UNIQUE (match_id, notification_type)
);

CREATE INDEX idx_match_notification_jobs_pending
    ON match_notification_jobs(next_attempt_at)
    WHERE status = 'PENDING';
