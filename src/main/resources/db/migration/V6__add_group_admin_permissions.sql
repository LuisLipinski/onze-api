CREATE TABLE group_admin_permissions (
    group_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE CASCADE,
    permission VARCHAR(48) NOT NULL,
    CONSTRAINT pk_group_admin_permissions PRIMARY KEY (group_member_id, permission)
);
