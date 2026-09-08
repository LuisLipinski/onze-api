package com.onze.api.group;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class GroupModels {

    private GroupModels() {
    }

    public record CreateGroupRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {
    }

    public record UpdateGroupDetailsRequest(
            @Size(max = 120) String city,
            @Size(max = 120) String mascot,
            @Size(max = 255) String venue,
            Boolean defaultPaymentEnabled,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal defaultPaymentAmount,
            @Size(max = 255) String defaultPixKey,
            List<@Valid ScheduleRequest> schedules) {
    }

    public record ScheduleRequest(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime startTime) {
    }

    public record ScheduleResponse(
            DayOfWeek dayOfWeek,
            LocalTime startTime) {
    }

    public record GroupResponse(
            UUID id,
            String name,
            String description,
            String photoUrl,
            String city,
            String mascot,
            String venue,
            BigDecimal defaultPaymentAmount,
            String defaultPixKey,
            List<ScheduleResponse> schedules,
            GroupRole role,
            Set<GroupAdminPermission> permissions,
            Instant createdAt) {
    }

    public record GroupMemberResponse(
            UUID membershipId,
            UUID userId,
            String displayName,
            GroupRole role,
            Set<GroupAdminPermission> permissions,
            boolean currentUser) {
    }

    public record UpdateAdminPermissionsRequest(
            @NotNull Set<@NotNull GroupAdminPermission> permissions) {
    }

    public record TransferPrimaryAdminRequest(
            @NotNull UUID replacementMemberId) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
