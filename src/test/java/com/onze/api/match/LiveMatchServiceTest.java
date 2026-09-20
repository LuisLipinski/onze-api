package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.match.LiveMatchService.InvalidLiveMatchTransitionException;
import com.onze.api.match.LiveMatchModels.LiveMatchStateResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveMatchServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T18:00:00Z");
    private FootballMatchRepository matches;
    private GroupMemberRepository members;
    private LiveMatchScoreRepository scores;
    private MatchTeamAssignmentRepository assignments;
    private MatchGoalEventRepository goals;
    private LiveMatchService service;
    private UUID matchId;
    private UUID groupId;
    private UUID adminId;
    private FootballMatch match;

    @BeforeEach
    void setUp() {
        matches = mock(FootballMatchRepository.class);
        members = mock(GroupMemberRepository.class);
        scores = mock(LiveMatchScoreRepository.class);
        assignments = mock(MatchTeamAssignmentRepository.class);
        goals = mock(MatchGoalEventRepository.class);
        service = new LiveMatchService(matches, members, scores, assignments, goals,
                Clock.fixed(NOW, ZoneOffset.UTC));
        matchId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        match = new FootballMatch(groupId, null, null, NOW.plusSeconds(3600), "UTC", "Arena", 14,
                MatchType.INTERNAL, 2, 2, MatchModality.FUT7, 14, null, null, true, null,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600), NOW.plusSeconds(1800), null, adminId);
        when(matches.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(members.findByGroupIdAndUserId(groupId, adminId))
                .thenReturn(Optional.of(new GroupMember(groupId, adminId, GroupRole.PRIMARY_ADMIN)));
        when(scores.findAllByMatchIdOrderBySideNumberAsc(matchId)).thenReturn(List.of());
        when(goals.save(any(MatchGoalEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startsAndFinishesMatchWithAuditTimestamps() {
        service.start(adminId.toString(), matchId);
        assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());
        assertEquals(NOW, match.getStartedAt());
        verify(scores, times(2)).save(org.mockito.ArgumentMatchers.any(LiveMatchScore.class));

        service.finish(adminId.toString(), matchId);
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(NOW, match.getFinishedAt());
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.finish(adminId.toString(), matchId));
        service.start(adminId.toString(), matchId);
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.start(adminId.toString(), matchId));
    }

    @Test
    void updatesAValidScoreOnlyWhileMatchIsInProgress() {
        LiveMatchScore teamOne = new LiveMatchScore(matchId, 1);
        when(scores.findByMatchIdAndSideNumber(matchId, 1)).thenReturn(Optional.of(teamOne));
        when(scores.findAllByMatchIdOrderBySideNumberAsc(matchId)).thenReturn(List.of(teamOne));

        service.start(adminId.toString(), matchId);
        LiveMatchStateResponse state = service.updateScore(adminId.toString(), matchId, 1, 3);

        assertEquals(3, teamOne.getScore());
        assertEquals(3, state.scores().getFirst().score());
    }

    @Test
    void rejectsNegativeScoreAndUnknownSide() {
        service.start(adminId.toString(), matchId);
        assertThrows(LiveMatchService.InvalidLiveMatchScoreException.class,
                () -> service.updateScore(adminId.toString(), matchId, 1, -1));
        assertThrows(LiveMatchService.InvalidLiveMatchScoreException.class,
                () -> service.updateScore(adminId.toString(), matchId, 3, 1));
    }

    @Test
    void resetsAnAccidentallyStartedMatchAndClearsItsScoreboard() {
        service.start(adminId.toString(), matchId);

        service.reset(adminId.toString(), matchId);

        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertEquals(null, match.getStartedAt());
        assertEquals(null, match.getFinishedAt());
        verify(scores).deleteAllByMatchId(matchId);
        verify(goals).deleteAllByMatchId(matchId);
    }

    @Test
    void rejectsResetBeforeTheMatchStarts() {
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.reset(adminId.toString(), matchId));
        verifyNoInteractions(scores, goals);
    }

    @Test
    void createsGoalWithServerElapsedTimeAndIncrementsScore() {
        service.start(adminId.toString(), matchId);
        MatchTeamAssignment scorer = assignment(1);
        LiveMatchScore teamOne = new LiveMatchScore(matchId, 1);
        when(assignments.findByIdAndMatchId(scorer.getId(), matchId)).thenReturn(Optional.of(scorer));
        when(scores.findByMatchIdAndSideNumber(matchId, 1)).thenReturn(Optional.of(teamOne));
        when(scores.findAllByMatchIdOrderBySideNumberAsc(matchId)).thenReturn(List.of(teamOne));

        var result = service.createGoal(adminId.toString(), matchId, scorer.getId(), null, true);

        assertEquals(1, teamOne.getScore());
        assertEquals(0, result.event().elapsedSeconds());
        assertEquals(true, result.event().penalty());
        assertEquals(null, result.event().assistAssignmentId());
        assertEquals(1, result.liveMatch().scores().getFirst().score());
    }

    @Test
    void rejectsPenaltyWithAssistAndAssistFromAnotherTeam() {
        service.start(adminId.toString(), matchId);
        MatchTeamAssignment scorer = assignment(1);
        MatchTeamAssignment otherTeam = assignment(2);

        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.createGoal(adminId.toString(), matchId, scorer.getId(), otherTeam.getId(), true));

        when(assignments.findByIdAndMatchId(scorer.getId(), matchId)).thenReturn(Optional.of(scorer));
        when(assignments.findByIdAndMatchId(otherTeam.getId(), matchId)).thenReturn(Optional.of(otherTeam));
        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.createGoal(adminId.toString(), matchId, scorer.getId(), otherTeam.getId(), false));
        verifyNoInteractions(goals);
    }

    private MatchTeamAssignment assignment(int teamNumber) {
        MatchTeamAssignment assignment = mock(MatchTeamAssignment.class);
        when(assignment.getId()).thenReturn(UUID.randomUUID());
        when(assignment.getTeamNumber()).thenReturn(teamNumber);
        when(assignment.getParticipantType()).thenReturn(TeamParticipantType.MEMBER);
        when(assignment.getParticipantId()).thenReturn(UUID.randomUUID());
        return assignment;
    }
}
