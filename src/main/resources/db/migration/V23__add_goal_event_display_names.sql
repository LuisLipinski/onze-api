ALTER TABLE match_goal_events
    ADD COLUMN scorer_display_name VARCHAR(120),
    ADD COLUMN assist_display_name VARCHAR(120);
