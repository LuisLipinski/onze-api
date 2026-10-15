-- Preserve the operational access already delegated before permissions were split.
INSERT INTO group_admin_permissions (group_member_id, permission)
SELECT existing.group_member_id, expanded.permission
FROM group_admin_permissions existing
JOIN group_members gm ON gm.id = existing.group_member_id
CROSS JOIN (VALUES
    ('CANCEL_GAMES'),
    ('MANAGE_MATCH_PLAYERS'),
    ('MANAGE_PAYMENTS'),
    ('MANAGE_LIVE_MATCH')
) AS expanded(permission)
WHERE existing.permission = 'SCHEDULE_GAMES' AND gm.role = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO group_admin_permissions (group_member_id, permission)
SELECT existing.group_member_id, 'MANAGE_TEAMS'
FROM group_admin_permissions existing
JOIN group_members gm ON gm.id = existing.group_member_id
WHERE existing.permission = 'EDIT_PLAYER_PROFILES' AND gm.role = 'ADMIN'
ON CONFLICT DO NOTHING;
