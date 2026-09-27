package com.onze.api.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
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
import com.onze.api.statistics.GroupStatisticsModels.PlayerMatchResult;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupStatisticsServiceTest {
    private GroupRepository groups;
    private GroupMemberRepository members;
    private UserRepository users;
    private FootballMatchRepository matches;
    private MatchTeamAssignmentRepository assignments;
    private MatchAttendanceRepository attendances;
    private LiveMatchScoreRepository scores;
    private MatchGoalEventRepository goals;
    private MatchTeamImageRepository teamImages;
    private GroupStatisticsService service;
    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;
    private UUID carolId;
    private UUID danaId;

    @BeforeEach
    void setUp() {
        groups = mock(GroupRepository.class);
        members = mock(GroupMemberRepository.class);
        users = mock(UserRepository.class);
        matches = mock(FootballMatchRepository.class);
        assignments = mock(MatchTeamAssignmentRepository.class);
        attendances = mock(MatchAttendanceRepository.class);
        scores = mock(LiveMatchScoreRepository.class);
        goals = mock(MatchGoalEventRepository.class);
        teamImages = mock(MatchTeamImageRepository.class);
        service = new GroupStatisticsService(
                groups, members, users, matches, assignments, attendances, scores, goals, teamImages);

        groupId = UUID.randomUUID();
        aliceId = UUID.randomUUID();
        bobId = UUID.randomUUID();
        carolId = UUID.randomUUID();
        danaId = UUID.randomUUID();
        when(groups.existsById(groupId)).thenReturn(true);
        doReturn(Optional.of(member(aliceId)))
                .when(members).findByGroupIdAndUserId(groupId, aliceId);
        doReturn(List.of(member(aliceId), member(bobId), member(carolId), member(danaId)))
                .when(members).findAllByGroupIdOrderByCreatedAtAsc(groupId);
        doReturn(List.of(
                user(aliceId, "Alice"),
                user(bobId, "Bruno"),
                user(carolId, "Carla"),
                user(danaId, "Davi")))
                .when(users).findAllById(any());
    }

    @Test
    void calculatesTotalsRankingsAndHistoryFromFinishedMatchSources() {
        UUID newestMatchId = UUID.randomUUID();
        UUID oldestMatchId = UUID.randomUUID();
        FootballMatch newest = match(
                newestMatchId,
                Instant.parse("2026-09-20T20:00:00Z"),
                Instant.parse("2026-09-20T22:00:00Z"),
                2);
        FootballMatch oldest = match(
                oldestMatchId,
                Instant.parse("2026-09-13T20:00:00Z"),
                Instant.parse("2026-09-13T22:00:00Z"),
                2);
        when(matches.findAllByGroupIdAndStatusOrderByFinishedAtDescStartsAtDesc(
                groupId, MatchStatus.FINISHED)).thenReturn(List.of(newest, oldest));
        doReturn(List.of(
                        assignment(newestMatchId, aliceId, 1),
                        assignment(newestMatchId, bobId, 2),
                        assignment(oldestMatchId, aliceId, 1),
                        assignment(oldestMatchId, bobId, 1),
                        assignment(oldestMatchId, carolId, 2)))
                .when(assignments)
                .findAllByMatchIdInAndParticipantTypeOrderByMatchIdAscTeamNumberAscCreatedAtAsc(
                        anyCollection(), any());
        doReturn(List.of(
                score(newestMatchId, 1, 2),
                score(newestMatchId, 2, 2),
                score(oldestMatchId, 1, 3),
                score(oldestMatchId, 2, 1)))
                .when(scores).findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(anyCollection());
        doReturn(List.of(
                        goal(newestMatchId, bobId, aliceId),
                        goal(oldestMatchId, aliceId, bobId),
                        goal(oldestMatchId, aliceId, null),
                        guestGoal(oldestMatchId)))
                .when(goals)
                .findAllByMatchIdInOrderByMatchIdAscElapsedSecondsAscCreatedAtAsc(anyCollection());
        doReturn(List.of(
                        identity(newestMatchId, 1, "Verde"),
                        identity(newestMatchId, 2, "Branco"),
                        identity(oldestMatchId, 1, "Leões"),
                        identity(oldestMatchId, 2, "Águias")))
                .when(teamImages).findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(anyCollection());

        var response = service.getGroup(aliceId.toString(), groupId);

        assertEquals(2, response.finishedMatches());
        assertEquals(4, response.registeredGoals());
        assertEquals(3, response.playersWithMatches());
        assertEquals("Alice", response.currentPlayer().displayName());
        assertEquals(2, response.currentPlayer().totals().gamesPlayed());
        assertEquals(1, response.currentPlayer().totals().wins());
        assertEquals(1, response.currentPlayer().totals().draws());
        assertEquals(2, response.currentPlayer().totals().goals());
        assertEquals(1, response.currentPlayer().totals().assists());
        assertEquals(List.of("Alice", "Bruno", "Carla", "Davi"),
                response.players().stream().map(item -> item.displayName()).toList());
        assertEquals(List.of(1, 2, 3, 3),
                response.rankings().goals().stream().map(item -> item.rank()).toList());
        assertEquals(List.of(2, 1, 0, 0),
                response.rankings().goals().stream().map(item -> item.value()).toList());
        assertEquals(newestMatchId, response.matchHistory().getFirst().matchId());
        assertEquals("Verde", response.matchHistory().getFirst().teams().getFirst().name());
        assertEquals(1, response.matchHistory().getFirst().registeredGoals());

        var player = service.getPlayer(aliceId.toString(), groupId, aliceId);

        assertEquals(2, player.matchHistory().size());
        assertEquals(PlayerMatchResult.DRAW, player.matchHistory().getFirst().result());
        assertEquals(PlayerMatchResult.WIN, player.matchHistory().getLast().result());
        assertEquals("Verde", player.matchHistory().getFirst().teamName());
        assertTrue(player.player().currentMember());
        assertTrue(player.player().currentUser());
    }

    @Test
    void treatsOnlyTheTopScoreTieAsADrawInMatchesWithThreeTeams() {
        UUID matchId = UUID.randomUUID();
        FootballMatch match = match(
                matchId,
                Instant.parse("2026-09-27T17:00:00Z"),
                Instant.parse("2026-09-27T19:00:00Z"),
                3);
        when(matches.findAllByGroupIdAndStatusOrderByFinishedAtDescStartsAtDesc(
                groupId, MatchStatus.FINISHED)).thenReturn(List.of(match));
        doReturn(List.of(
                        assignment(matchId, aliceId, 1),
                        assignment(matchId, bobId, 2),
                        assignment(matchId, carolId, 3)))
                .when(assignments)
                .findAllByMatchIdInAndParticipantTypeOrderByMatchIdAscTeamNumberAscCreatedAtAsc(
                        anyCollection(), any());
        doReturn(List.of(
                score(matchId, 1, 2),
                score(matchId, 2, 2),
                score(matchId, 3, 1)))
                .when(scores).findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(anyCollection());
        when(goals.findAllByMatchIdInOrderByMatchIdAscElapsedSecondsAscCreatedAtAsc(anyCollection()))
                .thenReturn(List.of());
        when(teamImages.findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(anyCollection()))
                .thenReturn(List.of());

        var response = service.getGroup(aliceId.toString(), groupId);

        assertEquals(1, statisticsFor(response, aliceId).totals().draws());
        assertEquals(1, statisticsFor(response, bobId).totals().draws());
        assertEquals(1, statisticsFor(response, carolId).totals().losses());
    }

    @Test
    void returnsZeroStatisticsForCurrentMembersWhenTheGroupHasNoFinishedMatches() {
        when(matches.findAllByGroupIdAndStatusOrderByFinishedAtDescStartsAtDesc(
                groupId, MatchStatus.FINISHED)).thenReturn(List.of());

        var response = service.getGroup(aliceId.toString(), groupId);

        assertEquals(0, response.finishedMatches());
        assertEquals(0, response.registeredGoals());
        assertEquals(0, response.playersWithMatches());
        assertEquals(4, response.players().size());
        assertEquals(0, response.currentPlayer().totals().gamesPlayed());
        assertTrue(response.matchHistory().isEmpty());
        verifyNoInteractions(assignments, attendances, scores, goals, teamImages);
    }

    @Test
    void countsConfirmedMembersOnTheGroupSideOfAnExternalMatch() {
        UUID matchId = UUID.randomUUID();
        FootballMatch match = externalMatch(
                matchId,
                Instant.parse("2026-09-25T20:00:00Z"),
                Instant.parse("2026-09-25T22:00:00Z"));
        when(matches.findAllByGroupIdAndStatusOrderByFinishedAtDescStartsAtDesc(
                groupId, MatchStatus.FINISHED)).thenReturn(List.of(match));
        doReturn(List.of(
                        attendance(matchId, aliceId),
                        attendance(matchId, bobId)))
                .when(attendances)
                .findAllByMatchIdInAndStatusOrderByMatchIdAscCreatedAtAsc(anyCollection(), any());
        doReturn(List.of(
                score(matchId, 1, 2),
                score(matchId, 2, 1)))
                .when(scores).findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(anyCollection());
        when(goals.findAllByMatchIdInOrderByMatchIdAscElapsedSecondsAscCreatedAtAsc(anyCollection()))
                .thenReturn(List.of());
        doReturn(List.of(
                        identity(matchId, 1, "Onze FC"),
                        identity(matchId, 2, "Adversário")))
                .when(teamImages).findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(anyCollection());

        var response = service.getGroup(aliceId.toString(), groupId);

        assertEquals(1, statisticsFor(response, aliceId).totals().gamesPlayed());
        assertEquals(1, statisticsFor(response, aliceId).totals().wins());
        assertEquals(1, statisticsFor(response, bobId).totals().wins());
        assertEquals(2, response.playersWithMatches());
        var player = service.getPlayer(aliceId.toString(), groupId, aliceId);
        assertEquals(1, player.matchHistory().getFirst().teamNumber());
        assertEquals("Onze FC", player.matchHistory().getFirst().teamName());
        verifyNoInteractions(assignments);
    }

    @Test
    void blocksUsersWhoAreNotCurrentGroupMembers() {
        UUID outsiderId = UUID.randomUUID();

        assertThrows(GroupAccessDeniedException.class,
                () -> service.getGroup(outsiderId.toString(), groupId));
        verifyNoInteractions(matches, assignments, attendances, scores, goals, teamImages);
    }

    @Test
    void keepsHistoricalPlayersMarkedAsFormerMembers() {
        UUID formerId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        doReturn(List.of(match(
                        matchId,
                        Instant.parse("2026-09-01T20:00:00Z"),
                        Instant.parse("2026-09-01T22:00:00Z"),
                        2)))
                .when(matches)
                .findAllByGroupIdAndStatusOrderByFinishedAtDescStartsAtDesc(
                        groupId, MatchStatus.FINISHED);
        doReturn(List.of(assignment(matchId, formerId, 1)))
                .when(assignments)
                .findAllByMatchIdInAndParticipantTypeOrderByMatchIdAscTeamNumberAscCreatedAtAsc(
                        anyCollection(), any());
        doReturn(List.of(score(matchId, 1, 1), score(matchId, 2, 0)))
                .when(scores).findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(anyCollection());
        when(goals.findAllByMatchIdInOrderByMatchIdAscElapsedSecondsAscCreatedAtAsc(anyCollection()))
                .thenReturn(List.of());
        when(teamImages.findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(anyCollection()))
                .thenReturn(List.of());
        doReturn(List.of(
                user(aliceId, "Alice"),
                user(bobId, "Bruno"),
                user(carolId, "Carla"),
                user(danaId, "Davi"),
                user(formerId, "Ex-jogador")))
                .when(users).findAllById(any());

        var response = service.getGroup(aliceId.toString(), groupId);
        var former = statisticsFor(response, formerId);

        assertFalse(former.currentMember());
        assertEquals(1, former.totals().gamesPlayed());
        assertEquals(1, former.totals().wins());
    }

    private GroupStatisticsModels.StatisticsPlayerResponse statisticsFor(
            GroupStatisticsModels.GroupStatisticsResponse response,
            UUID userId) {
        return response.players().stream()
                .filter(player -> player.userId().equals(userId))
                .findFirst()
                .orElseThrow();
    }

    private GroupMember member(UUID userId) {
        GroupMember member = mock(GroupMember.class);
        when(member.getUserId()).thenReturn(userId);
        return member;
    }

    private User user(UUID userId, String displayName) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getDisplayName()).thenReturn(displayName);
        return user;
    }

    private FootballMatch match(UUID matchId, Instant startsAt, Instant finishedAt, int teamCount) {
        FootballMatch match = mock(FootballMatch.class);
        when(match.getId()).thenReturn(matchId);
        when(match.getStartsAt()).thenReturn(startsAt);
        when(match.getFinishedAt()).thenReturn(finishedAt);
        when(match.getTimeZone()).thenReturn("America/Sao_Paulo");
        when(match.getVenue()).thenReturn("Arena Onze");
        when(match.getMatchType()).thenReturn(MatchType.INTERNAL);
        when(match.getTeamCount()).thenReturn(teamCount);
        return match;
    }

    private FootballMatch externalMatch(UUID matchId, Instant startsAt, Instant finishedAt) {
        FootballMatch match = mock(FootballMatch.class);
        when(match.getId()).thenReturn(matchId);
        when(match.getStartsAt()).thenReturn(startsAt);
        when(match.getFinishedAt()).thenReturn(finishedAt);
        when(match.getTimeZone()).thenReturn("America/Sao_Paulo");
        when(match.getVenue()).thenReturn("Arena Onze");
        when(match.getMatchType()).thenReturn(MatchType.VERSUS_EXTERNAL);
        return match;
    }

    private MatchAttendance attendance(UUID matchId, UUID userId) {
        MatchAttendance attendance = mock(MatchAttendance.class);
        when(attendance.getMatchId()).thenReturn(matchId);
        when(attendance.getUserId()).thenReturn(userId);
        return attendance;
    }

    private MatchTeamAssignment assignment(UUID matchId, UUID playerId, int teamNumber) {
        MatchTeamAssignment assignment = mock(MatchTeamAssignment.class);
        when(assignment.getMatchId()).thenReturn(matchId);
        when(assignment.getParticipantType()).thenReturn(TeamParticipantType.MEMBER);
        when(assignment.getParticipantId()).thenReturn(playerId);
        when(assignment.getTeamNumber()).thenReturn(teamNumber);
        return assignment;
    }

    private LiveMatchScore score(UUID matchId, int teamNumber, int score) {
        LiveMatchScore item = mock(LiveMatchScore.class);
        when(item.getMatchId()).thenReturn(matchId);
        when(item.getSideNumber()).thenReturn(teamNumber);
        when(item.getScore()).thenReturn(score);
        return item;
    }

    private MatchTeamImage identity(UUID matchId, int teamNumber, String name) {
        MatchTeamImage identity = mock(MatchTeamImage.class);
        when(identity.getMatchId()).thenReturn(matchId);
        when(identity.getTeamNumber()).thenReturn(teamNumber);
        when(identity.getTeamName()).thenReturn(name);
        when(identity.getImageUrl()).thenReturn("https://cdn.example/" + teamNumber + ".png");
        return identity;
    }

    private MatchGoalEvent goal(UUID matchId, UUID scorerId, UUID assistId) {
        MatchGoalEvent goal = mock(MatchGoalEvent.class);
        when(goal.getMatchId()).thenReturn(matchId);
        when(goal.getScorerParticipantType()).thenReturn(TeamParticipantType.MEMBER);
        when(goal.getScorerParticipantId()).thenReturn(scorerId);
        if (assistId != null) {
            when(goal.getAssistParticipantType()).thenReturn(TeamParticipantType.MEMBER);
            when(goal.getAssistParticipantId()).thenReturn(assistId);
        }
        return goal;
    }

    private MatchGoalEvent guestGoal(UUID matchId) {
        MatchGoalEvent goal = mock(MatchGoalEvent.class);
        when(goal.getMatchId()).thenReturn(matchId);
        when(goal.getScorerParticipantType()).thenReturn(TeamParticipantType.GUEST);
        when(goal.getScorerParticipantId()).thenReturn(UUID.randomUUID());
        return goal;
    }
}
