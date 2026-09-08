UPDATE group_members gm
SET role = 'PRIMARY_ADMIN'
FROM groups g
WHERE gm.group_id = g.id
  AND gm.user_id = g.created_by
  AND gm.role = 'ADMIN';

CREATE UNIQUE INDEX uk_group_members_primary_admin
    ON group_members(group_id)
    WHERE role = 'PRIMARY_ADMIN';
