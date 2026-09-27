package com.onze.api.statistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.AttendanceStatus;
import com.onze.api.match.FootballMatch;
import com.onze.api.match.FootballMatchRepository;
import com.onze.api.match.LiveMatchScore;
import com.onze.api.match.LiveMatchScoreRepository;
import com.onze.api.match.MatchAttendance;
import com.onze.api.match.MatchAttendanceRepository;
import com.onze.api.match.MatchGoalEvent;
import com.onze.api.match.MatchGoalEventRepository;
import com.onze.api.match.MatchStatus;
import com.onze.api.match.MatchTeamAssignment;
import com.onze.api.match.MatchTeamAssignmentRepository;
import com.onze.api.match.MatchTeamImage;
import com.onze.api.match.MatchTeamImageRepository;
import com.onze.api.match.MatchType;
import com.onze.api.match.TeamParticipantType;
import com.onze.api.statistics.GroupStatisticsModels.GroupMatchHistoryResponse;
import com.onze.api.statistics.GroupStatisticsModels.GroupStatisticsResponse;
import com.onze.api.statistics.GroupStatisticsModels.PlayerMatchHistoryResponse;
import com.onze.api.statistics.GroupStatisticsModels.PlayerMatchResult;
import com.onze.api.statistics.GroupStatisticsModels.PlayerStatisticsResponse;
import com.onze.api.statistics.GroupStatisticsModels.RankingEntryResponse;
import com.onze.api.statistics.GroupStatisticsModels.StatisticsPlayerResponse;
import com.onze.api.statistics.GroupStatisticsModels.StatisticsRankingsResponse;
import com.onze.api.statistics.GroupStatisticsModels.StatisticsTeamResponse;
import com.onze.api.statistics.GroupStatisticsModels.StatisticsTotalsResponse;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupStatisticsService {
    private static final Comparator<StatisticsPlayerResponse> PLAYER_NAME_ORDER = Comparator
            .comparing(StatisticsPlayerResponse::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(StatisticsPlayerResponse::userId);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final FootballMatchRepository matchRepository;
    private final MatchTeamAssignmentRepository assignmentRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final LiveMatchScoreRepository scoreRepository;
    private final MatchGoalEventRepository goalEventRepository;
    private final MatchTeamImageRepository teamImageRepository;

    public GroupStatisticsService(
            GroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            UserRepository userRepository,
            FootballMatchRepository matchRepository,
            MatchTeamAssignmentRepository assignmentRepository,
            MatchAttendanceRepository attendanceRepository,
            LiveMatchScoreRepository scoreRepository,
            MatchGoalEventRepository goalEventRepository,
            MatchTeamImageRepository teamImageRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.scoreRepository = scoreRepository;
        this.goalEventRepository = goalEventRepository;
        this.teamImageRepository = teamImageRepository;
    }

    @Transactional(readOnly = true)
    public GroupStatisticsResponse getGroup(String authenticatedUserId, UUID groupId) {
        UUID currentUserId = requireAccess(authenticatedUserId, groupId);
        StatisticsSnapshot snapshot = calculate(groupId, currentUserId);
        StatisticsPlayerResponse currentPlayer = snapshot.players().stream()
                .filter(player -> player.userId().equals(currentUserId))
                .findFirst()
                .orElseThrow(StatisticsPlayerNotFoundException::new);
        return new GroupStatisticsResponse(
                groupId,
                snapshot.matches().size(),
                snapshot.registeredGoals(),
                (int) snapshot.players().stream().filter(player -> player.totals().gamesPlayed() > 0).count(),
                currentPlayer,
                snapshot.players(),
                rankings(snapshot.players()),
                snapshot.groupHistory());
    }

    @Transactional(readOnly = true)
    public PlayerStatisticsResponse getPlayer(
            String authenticatedUserId,
            UUID groupId,
            UUID playerUserId) {
        UUID currentUserId = requireAccess(authenticatedUserId, groupId);
        StatisticsSnapshot snapshot = calculate(groupId, currentUserId);
        StatisticsPlayerResponse player = snapshot.players().stream()
                .filter(item -> item.userId().equals(playerUserId))
                .findFirst()
                .orElseThrow(StatisticsPlayerNotFoundException::new);
        return new PlayerStatisticsResponse(
                groupId,
                player,
                snapshot.playerHistory().getOrDefault(playerUserId, List.of()));
    }

    private UUID requireAccess(String authenticatedUserId, UUID groupId) {
        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException();
        }
        if (memberRepository.findByGroupIdAndUserId(groupId, userId).isEmpty()) {
            throw new GroupAccessDeniedException();
        }
        return userId;
    }

    private StatisticsSnapshot calculate(UUID groupId, UUID currentUserId) {
        List<FootballMatch> matches = matchRepository
                .findAllByGroupIdAndStatusOrderByFinishedAtDescStartsAtDesc(groupId, MatchStatus.FINISHED);
        List<UUID> matchIds = matches.stream().map(FootballMatch::getId).toList();
        List<UUID> internalMatchIds = matches.stream()
                .filter(match -> match.getMatchType() == MatchType.INTERNAL)
                .map(FootballMatch::getId)
                .toList();
        List<UUID> externalMatchIds = matches.stream()
                .filter(match -> match.getMatchType() == MatchType.VERSUS_EXTERNAL)
                .map(FootballMatch::getId)
                .toList();
        List<GroupMember> currentMembers = memberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId);

        List<MatchTeamAssignment> assignments = internalMatchIds.isEmpty()
                ? List.of()
                : assignmentRepository
                        .findAllByMatchIdInAndParticipantTypeOrderByMatchIdAscTeamNumberAscCreatedAtAsc(
                                internalMatchIds, TeamParticipantType.MEMBER);
        List<MatchAttendance> externalAttendances = externalMatchIds.isEmpty()
                ? List.of()
                : attendanceRepository.findAllByMatchIdInAndStatusOrderByMatchIdAscCreatedAtAsc(
                        externalMatchIds, AttendanceStatus.GOING);
        List<LiveMatchScore> scores = matchIds.isEmpty()
                ? List.of()
                : scoreRepository.findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(matchIds);
        List<MatchGoalEvent> goals = matchIds.isEmpty()
                ? List.of()
                : goalEventRepository
                        .findAllByMatchIdInOrderByMatchIdAscElapsedSecondsAscCreatedAtAsc(matchIds);
        List<MatchTeamImage> identities = matchIds.isEmpty()
                ? List.of()
                : teamImageRepository.findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(matchIds);

        Set<UUID> currentMemberIds = currentMembers.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> playerIds = new LinkedHashSet<>(currentMemberIds);
        assignments.stream().map(MatchTeamAssignment::getParticipantId).forEach(playerIds::add);
        externalAttendances.stream().map(MatchAttendance::getUserId).forEach(playerIds::add);
        goals.stream()
                .filter(goal -> goal.getScorerParticipantType() == TeamParticipantType.MEMBER)
                .map(MatchGoalEvent::getScorerParticipantId)
                .forEach(playerIds::add);
        goals.stream()
                .filter(goal -> goal.getAssistParticipantType() == TeamParticipantType.MEMBER)
                .map(MatchGoalEvent::getAssistParticipantId)
                .forEach(playerIds::add);

        Map<UUID, User> users = userRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<UUID, MutablePlayerStatistics> playerStats = new LinkedHashMap<>();
        for (UUID playerId : playerIds) {
            User user = users.get(playerId);
            String displayName = user == null ? "Jogador" : user.getDisplayName();
            playerStats.put(playerId, new MutablePlayerStatistics(
                    playerId,
                    displayName,
                    currentMemberIds.contains(playerId),
                    playerId.equals(currentUserId)));
        }

        List<Participation> participations = new ArrayList<>(assignments.size() + externalAttendances.size());
        assignments.stream()
                .map(assignment -> new Participation(
                        assignment.getMatchId(), assignment.getParticipantId(), assignment.getTeamNumber()))
                .forEach(participations::add);
        externalAttendances.stream()
                .map(attendance -> new Participation(attendance.getMatchId(), attendance.getUserId(), 1))
                .forEach(participations::add);
        Map<UUID, List<Participation>> participationsByMatch = groupByMatch(
                participations, Participation::matchId);
        Map<UUID, List<LiveMatchScore>> scoresByMatch = groupByMatch(scores, LiveMatchScore::getMatchId);
        Map<UUID, List<MatchGoalEvent>> goalsByMatch = groupByMatch(goals, MatchGoalEvent::getMatchId);
        Map<UUID, List<MatchTeamImage>> identitiesByMatch = groupByMatch(identities, MatchTeamImage::getMatchId);

        List<GroupMatchHistoryResponse> groupHistory = new ArrayList<>(matches.size());
        Map<UUID, List<PlayerMatchHistoryResponse>> playerHistory = new HashMap<>();
        for (FootballMatch match : matches) {
            UUID matchId = match.getId();
            List<MatchGoalEvent> matchGoals = goalsByMatch.getOrDefault(matchId, List.of());
            List<StatisticsTeamResponse> teams = teams(
                    match,
                    scoresByMatch.getOrDefault(matchId, List.of()),
                    identitiesByMatch.getOrDefault(matchId, List.of()));
            groupHistory.add(new GroupMatchHistoryResponse(
                    matchId,
                    match.getStartsAt(),
                    match.getFinishedAt(),
                    match.getTimeZone(),
                    match.getVenue(),
                    matchGoals.size(),
                    teams));

            Map<UUID, Integer> goalsByPlayer = countGoals(matchGoals);
            Map<UUID, Integer> assistsByPlayer = countAssists(matchGoals);
            for (Participation participation : participationsByMatch.getOrDefault(matchId, List.of())) {
                MutablePlayerStatistics statistics = playerStats.get(participation.userId());
                if (statistics == null) {
                    continue;
                }
                PlayerMatchResult result = resultForTeam(teams, participation.teamNumber());
                int playerGoals = goalsByPlayer.getOrDefault(statistics.userId, 0);
                int playerAssists = assistsByPlayer.getOrDefault(statistics.userId, 0);
                statistics.addMatch(result);
                StatisticsTeamResponse ownTeam = teams.stream()
                        .filter(team -> team.teamNumber() == participation.teamNumber())
                        .findFirst()
                        .orElse(new StatisticsTeamResponse(
                                participation.teamNumber(),
                                "Time " + participation.teamNumber(),
                                null,
                                0));
                playerHistory.computeIfAbsent(statistics.userId, ignored -> new ArrayList<>())
                        .add(new PlayerMatchHistoryResponse(
                                matchId,
                                match.getStartsAt(),
                                match.getFinishedAt(),
                                match.getTimeZone(),
                                match.getVenue(),
                                participation.teamNumber(),
                                ownTeam.name(),
                                ownTeam.imageUrl(),
                                result,
                                playerGoals,
                                playerAssists,
                                teams));
            }
        }

        for (MatchGoalEvent goal : goals) {
            if (goal.getScorerParticipantType() == TeamParticipantType.MEMBER) {
                MutablePlayerStatistics statistics = playerStats.get(goal.getScorerParticipantId());
                if (statistics != null) {
                    statistics.addGoal();
                }
            }
            if (goal.getAssistParticipantType() == TeamParticipantType.MEMBER) {
                MutablePlayerStatistics statistics = playerStats.get(goal.getAssistParticipantId());
                if (statistics != null) {
                    statistics.addAssist();
                }
            }
        }

        List<StatisticsPlayerResponse> players = playerStats.values().stream()
                .map(MutablePlayerStatistics::response)
                .sorted(PLAYER_NAME_ORDER)
                .toList();
        return new StatisticsSnapshot(
                matches,
                goals.size(),
                players,
                List.copyOf(groupHistory),
                immutableHistory(playerHistory));
    }

    private Map<UUID, Integer> countGoals(List<MatchGoalEvent> goals) {
        Map<UUID, Integer> result = new HashMap<>();
        for (MatchGoalEvent goal : goals) {
            if (goal.getScorerParticipantType() == TeamParticipantType.MEMBER) {
                result.merge(goal.getScorerParticipantId(), 1, Integer::sum);
            }
        }
        return result;
    }

    private Map<UUID, Integer> countAssists(List<MatchGoalEvent> goals) {
        Map<UUID, Integer> result = new HashMap<>();
        for (MatchGoalEvent goal : goals) {
            if (goal.getAssistParticipantType() == TeamParticipantType.MEMBER) {
                result.merge(goal.getAssistParticipantId(), 1, Integer::sum);
            }
        }
        return result;
    }

    private List<StatisticsTeamResponse> teams(
            FootballMatch match,
            List<LiveMatchScore> scores,
            List<MatchTeamImage> identities) {
        Map<Integer, Integer> scoreByTeam = scores.stream().collect(Collectors.toMap(
                LiveMatchScore::getSideNumber,
                LiveMatchScore::getScore,
                (first, ignored) -> first));
        Map<Integer, MatchTeamImage> identityByTeam = identities.stream().collect(Collectors.toMap(
                MatchTeamImage::getTeamNumber,
                identity -> identity,
                (first, ignored) -> first));
        List<StatisticsTeamResponse> result = new ArrayList<>();
        for (int teamNumber = 1; teamNumber <= sideCount(match); teamNumber++) {
            MatchTeamImage identity = identityByTeam.get(teamNumber);
            result.add(new StatisticsTeamResponse(
                    teamNumber,
                    identity == null ? "Time " + teamNumber : identity.getTeamName(),
                    identity == null ? null : identity.getImageUrl(),
                    scoreByTeam.getOrDefault(teamNumber, 0)));
        }
        return List.copyOf(result);
    }

    private PlayerMatchResult resultForTeam(List<StatisticsTeamResponse> teams, int teamNumber) {
        int teamScore = teams.stream()
                .filter(team -> team.teamNumber() == teamNumber)
                .mapToInt(StatisticsTeamResponse::score)
                .findFirst()
                .orElse(0);
        int highestScore = teams.stream().mapToInt(StatisticsTeamResponse::score).max().orElse(0);
        if (teamScore < highestScore) {
            return PlayerMatchResult.LOSS;
        }
        long leaders = teams.stream().filter(team -> team.score() == highestScore).count();
        return leaders == 1 ? PlayerMatchResult.WIN : PlayerMatchResult.DRAW;
    }

    private int sideCount(FootballMatch match) {
        return match.getMatchType() == MatchType.INTERNAL
                ? Math.max(2, match.getTeamCount() == null ? 2 : match.getTeamCount())
                : 2;
    }

    private StatisticsRankingsResponse rankings(List<StatisticsPlayerResponse> players) {
        return new StatisticsRankingsResponse(
                ranking(players, player -> player.totals().goals()),
                ranking(players, player -> player.totals().assists()),
                ranking(players, player -> player.totals().gamesPlayed()),
                ranking(players, player -> player.totals().wins()));
    }

    private List<RankingEntryResponse> ranking(
            List<StatisticsPlayerResponse> players,
            ToIntFunction<StatisticsPlayerResponse> valueExtractor) {
        List<StatisticsPlayerResponse> ordered = players.stream()
                .sorted(Comparator
                        .comparingInt(valueExtractor).reversed()
                        .thenComparing(PLAYER_NAME_ORDER))
                .toList();
        List<RankingEntryResponse> result = new ArrayList<>(ordered.size());
        Integer previousValue = null;
        int rank = 0;
        for (int index = 0; index < ordered.size(); index++) {
            StatisticsPlayerResponse player = ordered.get(index);
            int value = valueExtractor.applyAsInt(player);
            if (previousValue == null || value != previousValue) {
                rank = index + 1;
                previousValue = value;
            }
            result.add(new RankingEntryResponse(
                    rank,
                    player.userId(),
                    player.displayName(),
                    player.currentUser(),
                    value));
        }
        return List.copyOf(result);
    }

    private <T> Map<UUID, List<T>> groupByMatch(
            Collection<T> values,
            java.util.function.Function<T, UUID> matchIdExtractor) {
        return values.stream().collect(Collectors.groupingBy(
                matchIdExtractor,
                LinkedHashMap::new,
                Collectors.toList()));
    }

    private Map<UUID, List<PlayerMatchHistoryResponse>> immutableHistory(
            Map<UUID, List<PlayerMatchHistoryResponse>> history) {
        Map<UUID, List<PlayerMatchHistoryResponse>> result = new HashMap<>();
        history.forEach((userId, matches) -> result.put(userId, List.copyOf(matches)));
        return Map.copyOf(result);
    }

    private static final class MutablePlayerStatistics {
        private final UUID userId;
        private final String displayName;
        private final boolean currentMember;
        private final boolean currentUser;
        private int gamesPlayed;
        private int wins;
        private int draws;
        private int losses;
        private int goals;
        private int assists;

        private MutablePlayerStatistics(
                UUID userId,
                String displayName,
                boolean currentMember,
                boolean currentUser) {
            this.userId = userId;
            this.displayName = displayName;
            this.currentMember = currentMember;
            this.currentUser = currentUser;
        }

        private void addMatch(PlayerMatchResult result) {
            gamesPlayed++;
            switch (result) {
                case WIN -> wins++;
                case DRAW -> draws++;
                case LOSS -> losses++;
            }
        }

        private void addGoal() {
            goals++;
        }

        private void addAssist() {
            assists++;
        }

        private StatisticsPlayerResponse response() {
            return new StatisticsPlayerResponse(
                    userId,
                    displayName,
                    currentMember,
                    currentUser,
                    new StatisticsTotalsResponse(gamesPlayed, wins, draws, losses, goals, assists));
        }
    }

    private record StatisticsSnapshot(
            List<FootballMatch> matches,
            int registeredGoals,
            List<StatisticsPlayerResponse> players,
            List<GroupMatchHistoryResponse> groupHistory,
            Map<UUID, List<PlayerMatchHistoryResponse>> playerHistory) { }

    private record Participation(UUID matchId, UUID userId, int teamNumber) { }

    public static final class StatisticsPlayerNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
