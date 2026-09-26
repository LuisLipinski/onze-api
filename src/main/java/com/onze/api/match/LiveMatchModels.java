package com.onze.api.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LiveMatchModels {
    private LiveMatchModels() { }

    public record UpdateLiveScoreRequest(
            @NotNull @Min(1) Integer sideNumber,
            @NotNull @Min(0) Integer score) { }

    public record StartLiveMatchRequest(
            @Size(max = 16) List<@Valid TeamIdentityNameRequest> teams) { }

    public record TeamIdentityNameRequest(
            @NotNull @Min(1) Integer teamNumber,
            @NotBlank @Size(max = 80) String name) { }

    public record CreateGoalEventRequest(
            @NotNull UUID scorerAssignmentId,
            UUID assistAssignmentId,
            @NotNull Boolean penalty) { }

    public record CreateCardEventRequest(
            @NotNull UUID playerAssignmentId,
            @NotNull MatchCardType cardType) { }

    public record LiveScoreSideResponse(
            int sideNumber,
            int score,
            String name,
            String imageUrl) {
        public LiveScoreSideResponse(int sideNumber, int score) {
            this(sideNumber, score, "Time " + sideNumber, null);
        }
    }

    public record LiveMatchStateResponse(
            UUID matchId,
            MatchStatus status,
            Instant startedAt,
            Instant finishedAt,
            long version,
            List<LiveScoreSideResponse> scores,
            List<GoalEventResponse> goalEvents,
            List<CardEventResponse> cardEvents,
            boolean canManage) { }

    public record LiveMatchSnapshotResponse(
            UUID matchId,
            MatchStatus status,
            Instant startedAt,
            Instant finishedAt,
            long version,
            List<LiveScoreSideResponse> scores,
            List<GoalEventResponse> goalEvents,
            List<CardEventResponse> cardEvents) { }

    public record LiveMatchSummaryResponse(
            UUID matchId,
            UUID groupId,
            String groupName,
            Instant startsAt,
            String timeZone,
            String venue,
            MatchStatus status,
            Instant startedAt,
            MatchType matchType,
            Integer teamCount,
            long version,
            List<LiveScoreSideResponse> scores,
            boolean canManage) { }

    public record LiveMatchStreamEventResponse(
            LiveMatchChangeType type,
            UUID matchId,
            UUID groupId,
            long version,
            LiveMatchSummaryResponse summary,
            LiveMatchSnapshotResponse liveMatch) { }

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
