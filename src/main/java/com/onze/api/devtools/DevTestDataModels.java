package com.onze.api.devtools;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class DevTestDataModels {

    private DevTestDataModels() {
    }

    public record GeneratePlayersRequest(
            @Min(2) @Max(40) int count) {
    }

    public record ApplyScenarioRequest(
            @NotNull DevTestDataScenario scenario) {
    }

    public record StatusResponse(
            UUID matchId,
            UUID groupId,
            int testPlayers,
            int testPlayersGoing,
            long occupiedSpots,
            int maxPlayers,
            List<DevTestDataScenario> scenarios) {
    }

    public record GeneratePlayersResponse(
            int created,
            int reused,
            int totalTestPlayers) {
    }

    public record ApplyScenarioResponse(
            DevTestDataScenario scenario,
            int updatedPlayers) {
    }

    public record MatchAttendanceResponse(
            int added,
            int alreadyGoing,
            int removed,
            int skippedCapacity,
            long occupiedSpots,
            int maxPlayers) {
    }
}
