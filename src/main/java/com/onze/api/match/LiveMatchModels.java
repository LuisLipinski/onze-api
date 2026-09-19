package com.onze.api.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class LiveMatchModels {
    private LiveMatchModels() { }

    public record UpdateLiveScoreRequest(
            @NotNull @Min(1) Integer sideNumber,
            @NotNull @Min(0) Integer score) { }

    public record LiveScoreSideResponse(int sideNumber, int score) { }

    public record LiveMatchStateResponse(
            UUID matchId,
            MatchStatus status,
            Instant startedAt,
            Instant finishedAt,
            List<LiveScoreSideResponse> scores,
            boolean canManage) { }
}
