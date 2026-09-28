package com.onze.api.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
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

    public record UpdatePeriodAddedTimeRequest(
            @NotNull @Min(0) @Max(180) Integer minutes) { }

    public record PenaltyTakerRequest(
            @NotNull @Min(1) @Max(2) Integer teamNumber,
            @NotNull @Min(1) @Max(5) Integer kickOrder,
            UUID assignmentId,
            @Size(max = 120) String displayName) { }

    public record SetPenaltyLineupRequest(
            @NotNull @Size(min = 10, max = 10) List<@Valid PenaltyTakerRequest> takers) { }

    public record RecordPenaltyAttemptRequest(
            @NotNull Boolean scored,
            UUID takerAssignmentId,
            @Size(max = 120) String takerDisplayName) { }

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
            LiveMatchPhase phase,
            List<MatchPeriodResponse> periods,
            PenaltyShootoutResponse penaltyShootout,
            boolean canManage) { }

    public record LiveMatchSnapshotResponse(
            UUID matchId,
            MatchStatus status,
            Instant startedAt,
            Instant finishedAt,
            long version,
            List<LiveScoreSideResponse> scores,
            List<GoalEventResponse> goalEvents,
            List<CardEventResponse> cardEvents,
            LiveMatchPhase phase,
            List<MatchPeriodResponse> periods,
            PenaltyShootoutResponse penaltyShootout) { }

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
            LiveMatchPhase phase,
            MatchPeriodResponse currentPeriod,
            boolean canManage) { }

    public record MatchPeriodResponse(
            UUID id,
            MatchPeriodType periodType,
            int periodNumber,
            int durationMinutes,
            Integer addedTimeMinutes,
            Instant startedAt,
            Instant endedAt) { }

    public record PenaltyTakerResponse(
            int teamNumber,
            int kickOrder,
            UUID assignmentId,
            TeamParticipantType participantType,
            UUID participantId,
            String displayName) { }

    public record PenaltyAttemptResponse(
            UUID id,
            int sequenceNumber,
            int roundNumber,
            int teamNumber,
            UUID assignmentId,
            TeamParticipantType participantType,
            UUID participantId,
            String displayName,
            boolean scored,
            Instant createdAt) { }

    public record PenaltyShootoutResponse(
            PenaltyShootoutStatus status,
            int teamOneScore,
            int teamTwoScore,
            int teamOneAttempts,
            int teamTwoAttempts,
            Integer nextTeamNumber,
            Integer nextRoundNumber,
            boolean nextTakerSelectionRequired,
            PenaltyTakerResponse nextTaker,
            Integer winnerTeamNumber,
            List<PenaltyTakerResponse> takers,
            List<PenaltyAttemptResponse> attempts) { }

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
            MatchPeriodType periodType,
            Integer periodNumber,
            Long periodElapsedSeconds,
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
            MatchPeriodType periodType,
            Integer periodNumber,
            Long periodElapsedSeconds,
            Instant createdAt) { }

    public record CreateCardEventResponse(CardEventResponse event, LiveMatchStateResponse liveMatch) { }
}
