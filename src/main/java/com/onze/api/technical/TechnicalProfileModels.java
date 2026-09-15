package com.onze.api.technical;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.onze.api.group.PlayerPosition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TechnicalProfileModels {

    private TechnicalProfileModels() {
    }

    public record UpdateTechnicalProfileRequest(
            @NotNull @Size(max = 17) Map<@NotNull PlayerSkill, Integer> ratings) {
    }

    public record OverallResponse(
            Integer overall,
            int coverage,
            boolean reliable,
            boolean estimated,
            PlayerPosition resolvedPosition,
            List<PlayerSkill> missingEssentialSkills) {
    }

    public record PositionOverallResponse(
            PlayerPosition position,
            Integer overall,
            int coverage,
            boolean reliable,
            boolean estimated,
            PlayerPosition resolvedPosition,
            List<PlayerSkill> missingEssentialSkills) {
    }

    public record TechnicalProfileResponse(
            UUID membershipId,
            UUID userId,
            String displayName,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            Map<PlayerSkill, Integer> ratings,
            OverallResponse generalOverall,
            List<PositionOverallResponse> positionOveralls,
            Map<PlayerPosition, List<PlayerSkill>> importantSkills,
            Instant technicalProfileUpdatedAt) {
    }
}
