package com.onze.api.group;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class GroupInviteModels {

    private GroupInviteModels() {
    }

    public record InviteResponse(
            UUID groupId,
            String code,
            String deepLink,
            String shareUrl) {
    }

    public record JoinGroupRequest(
            @NotBlank
            @Pattern(regexp = "[A-Za-z2-9]{8}")
            String code) {
    }

    public record JoinGroupResponse(
            UUID groupId,
            String groupName,
            GroupRole role,
            boolean alreadyMember) {
    }
}
