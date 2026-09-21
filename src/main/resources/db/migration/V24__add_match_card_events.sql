CREATE TABLE match_card_events (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES football_matches(id) ON DELETE CASCADE,
    side_number INTEGER NOT NULL,
    player_assignment_id UUID NOT NULL,
    player_participant_type VARCHAR(32) NOT NULL,
    player_participant_id UUID NOT NULL,
    player_display_name VARCHAR(120) NOT NULL,
    card_type VARCHAR(16) NOT NULL,
    elapsed_seconds BIGINT NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_match_card_events_side_number CHECK (side_number >= 1),
    CONSTRAINT ck_match_card_events_elapsed_seconds CHECK (elapsed_seconds >= 0),
    CONSTRAINT ck_match_card_events_type CHECK (card_type IN ('YELLOW', 'RED'))
);

CREATE INDEX ix_match_card_events_match_time
    ON match_card_events(match_id, elapsed_seconds, created_at);
