package com.onze.api.group;

import java.util.UUID;

public final class GroupInviteModels {

    private GroupInviteModels() {
    }

    public record InviteResponse(
            UUID groupId,
            String code,
            String deepLink) {
    }
}
