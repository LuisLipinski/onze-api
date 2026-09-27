package com.onze.api.statistics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
            int assists) { }

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
            List<RankingEntryResponse> wins) { }

    public record StatisticsTeamResponse(
            int teamNumber,
            String name,
            String imageUrl,
            int score) { }

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
            List<StatisticsTeamResponse> teams) { }

    public record GroupStatisticsResponse(
            UUID groupId,
            int finishedMatches,
            int registeredGoals,
            int playersWithMatches,
            StatisticsPlayerResponse currentPlayer,
            List<StatisticsPlayerResponse> players,
            StatisticsRankingsResponse rankings,
            List<GroupMatchHistoryResponse> matchHistory) { }

    public record PlayerStatisticsResponse(
            UUID groupId,
            StatisticsPlayerResponse player,
            List<PlayerMatchHistoryResponse> matchHistory) { }
}
