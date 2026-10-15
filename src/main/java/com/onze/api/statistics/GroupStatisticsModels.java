package com.onze.api.statistics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public final class GroupStatisticsModels {
    private GroupStatisticsModels() { }

    public enum PlayerMatchResult {
        WIN,
        DRAW,
        LOSS
    }

    public record StatisticsTotalsResponse(
            int gamesPlayed,
            int wins,
            int draws,
            int losses,
            int goals,
            int assists,
            int foulsCommitted,
            int foulsSuffered,
            int penaltiesCommitted,
            int penaltiesWon,
            int penaltiesTaken,
            int penaltiesScored,
            int penaltiesMissed) { }

    public record StatisticsPlayerResponse(
            UUID userId,
            String displayName,
            boolean currentMember,
            boolean currentUser,
            StatisticsTotalsResponse totals) { }

    public record RankingEntryResponse(
            int rank,
            UUID userId,
            String displayName,
            boolean currentUser,
            int value) { }

    public record StatisticsRankingsResponse(
            List<RankingEntryResponse> goals,
            List<RankingEntryResponse> assists,
            List<RankingEntryResponse> gamesPlayed,
            List<RankingEntryResponse> wins,
            List<RankingEntryResponse> losses,
            List<RankingEntryResponse> foulsCommitted,
            List<RankingEntryResponse> foulsSuffered,
            List<RankingEntryResponse> penaltiesCommitted,
            List<RankingEntryResponse> penaltiesWon,
            List<RankingEntryResponse> penaltiesTaken,
            List<RankingEntryResponse> penaltiesScored,
            List<RankingEntryResponse> penaltiesMissed) { }

    public record StatisticsTeamResponse(
            int teamNumber,
            String name,
            String imageUrl,
            int score,
            int foulsCommitted,
            int foulsSuffered,
            int penaltiesCommitted,
            int penaltiesWon,
            int penaltiesScored,
            int penaltiesMissed) { }

    public record GroupMatchHistoryResponse(
            UUID matchId,
            Instant startsAt,
            Instant finishedAt,
            String timeZone,
            String venue,
            int registeredGoals,
            List<StatisticsTeamResponse> teams) { }

    public record PlayerMatchHistoryResponse(
            UUID matchId,
            Instant startsAt,
            Instant finishedAt,
            String timeZone,
            String venue,
            int teamNumber,
            String teamName,
            String teamImageUrl,
            PlayerMatchResult result,
            int goals,
            int assists,
            int foulsCommitted,
            int foulsSuffered,
            int penaltiesCommitted,
            int penaltiesWon,
            int penaltiesTaken,
            int penaltiesScored,
            int penaltiesMissed,
            List<StatisticsTeamResponse> teams) { }

    public record GroupStatisticsResponse(
            UUID groupId,
            List<StatisticsSeasonResponse> seasons,
            UUID selectedSeasonId,
            boolean canManageSeasons,
            int finishedMatches,
            int registeredGoals,
            int playersWithMatches,
            StatisticsPlayerResponse currentPlayer,
            List<StatisticsPlayerResponse> players,
            StatisticsRankingsResponse rankings,
            List<GroupMatchHistoryResponse> matchHistory) { }

    public record PlayerStatisticsResponse(
            UUID groupId,
            StatisticsSeasonResponse season,
            StatisticsPlayerResponse player,
            List<PlayerMatchHistoryResponse> matchHistory) { }

    public record StatisticsSeasonResponse(UUID id, int number, Instant startedAt, Instant endedAt) { }

    public record StartStatisticsSeasonRequest(@NotNull UUID expectedCurrentSeasonId) { }

    public record SeasonStatisticsSnapshot(
            List<UUID> matchIds,
            int registeredGoals,
            List<StatisticsPlayerResponse> players,
            StatisticsRankingsResponse rankings,
            List<GroupMatchHistoryResponse> groupHistory,
            Map<UUID, List<PlayerMatchHistoryResponse>> playerHistory) { }
}
