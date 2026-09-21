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

    public record CreateGoalEventRequest(
            @NotNull UUID scorerAssignmentId,
            UUID assistAssignmentId,
            @NotNull Boolean penalty) { }

    public record CreateCardEventRequest(
            @NotNull UUID playerAssignmentId,
            @NotNull MatchCardType cardType) { }

    public record LiveScoreSideResponse(int sideNumber, int score) { }

    public record LiveMatchStateResponse(
            UUID matchId,
            MatchStatus status,
            Instant startedAt,
            Instant finishedAt,
            List<LiveScoreSideResponse> scores,
            List<GoalEventResponse> goalEvents,
            List<CardEventResponse> cardEvents,
            boolean canManage) { }

    public record GoalEventResponse(
            UUID id,
            UUID matchId,
            int sideNumber,
            UUID scorerAssignmentId,
            TeamParticipantType scorerParticipantType,
            UUID scorerParticipantId,
            String scorerDisplayName,
            UUID assistAssignmentId,
            TeamParticipantType assistParticipantType,
            UUID assistParticipantId,
            String assistDisplayName,
            boolean penalty,
            long elapsedSeconds,
            Instant createdAt) { }

    public record CreateGoalEventResponse(GoalEventResponse event, LiveMatchStateResponse liveMatch) { }

    public record CardEventResponse(
            UUID id,
            UUID matchId,
            int sideNumber,
            UUID playerAssignmentId,
            TeamParticipantType playerParticipantType,
            UUID playerParticipantId,
            String playerDisplayName,
            MatchCardType cardType,
            long elapsedSeconds,
            Instant createdAt) { }

    public record CreateCardEventResponse(CardEventResponse event, LiveMatchStateResponse liveMatch) { }
}
