package com.onze.api.group;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
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
            List<ScheduleResponse> schedules,
            GroupRole role,
            Instant createdAt) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
