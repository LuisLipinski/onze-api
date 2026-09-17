package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public final class MatchTeamReserveModels {

    private MatchTeamReserveModels() {
    }

    public record UpdateMatchTeamReservesRequest(
            @NotNull List<@NotNull UUID> reserveAssignmentIds) {
    }

    public record MatchTeamReservesResponse(
            UUID matchId,
            List<UUID> reserveAssignmentIds) {
    }
}
