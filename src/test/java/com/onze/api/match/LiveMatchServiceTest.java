package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveMatchServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T18:00:00Z");
    private FootballMatchRepository matches;
    private GroupMemberRepository members;
    private LiveMatchScoreRepository scores;
    private MatchTeamAssignmentRepository assignments;
    private MatchGoalEventRepository goals;
    private MatchCardEventRepository cards;
    private UserRepository users;
    private MatchGuestRepository guests;
    private MatchRentalGoalkeeperRepository rentalGoalkeepers;
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
        cards = mock(MatchCardEventRepository.class);
        users = mock(UserRepository.class);
        guests = mock(MatchGuestRepository.class);
        rentalGoalkeepers = mock(MatchRentalGoalkeeperRepository.class);
        service = new LiveMatchService(matches, members, scores, assignments, goals, cards,
                users, guests, rentalGoalkeepers,
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
        when(goals.findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(matchId)).thenReturn(List.of());
        when(cards.findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(matchId)).thenReturn(List.of());
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
        verify(cards).deleteAllByMatchId(matchId);
    }

    @Test
    void rejectsResetBeforeTheMatchStarts() {
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.reset(adminId.toString(), matchId));
        verifyNoInteractions(scores, goals, cards);
    }

    @Test
    void createsGoalWithServerElapsedTimeAndIncrementsScore() {
        service.start(adminId.toString(), matchId);
        MatchTeamAssignment scorer = assignment(1);
        LiveMatchScore teamOne = new LiveMatchScore(matchId, 1);
        when(assignments.findByIdAndMatchId(scorer.getId(), matchId)).thenReturn(Optional.of(scorer));
        when(scores.findByMatchIdAndSideNumber(matchId, 1)).thenReturn(Optional.of(teamOne));
        when(scores.findAllByMatchIdOrderBySideNumberAsc(matchId)).thenReturn(List.of(teamOne));
        when(users.findById(scorer.getParticipantId()))
                .thenReturn(Optional.of(new User("scorer@example.invalid", "hash", "Artilheiro")));

        var result = service.createGoal(adminId.toString(), matchId, scorer.getId(), null, true);

        assertEquals(1, teamOne.getScore());
        assertEquals(0, result.event().elapsedSeconds());
        assertEquals(true, result.event().penalty());
        assertEquals("Artilheiro", result.event().scorerDisplayName());
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

    @Test
    void createsCardWithPlayerTeamAndServerTime() {
        service.start(adminId.toString(), matchId);
        MatchTeamAssignment player = assignment(2);
        when(assignments.findByIdAndMatchId(player.getId(), matchId)).thenReturn(Optional.of(player));
        when(users.findById(player.getParticipantId()))
                .thenReturn(Optional.of(new User("player@example.invalid", "hash", "Jogador Teste")));
        when(cards.save(any(MatchCardEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createCard(adminId.toString(), matchId, player.getId(), MatchCardType.YELLOW);

        assertEquals(2, result.event().sideNumber());
        assertEquals("Jogador Teste", result.event().playerDisplayName());
        assertEquals(MatchCardType.YELLOW, result.event().cardType());
        assertEquals(0, result.event().elapsedSeconds());
    }

    @Test
    void directRedCardBlocksGoalsAssistsAndNewCards() {
        service.start(adminId.toString(), matchId);
        MatchTeamAssignment sentOff = assignment(1);
        MatchTeamAssignment active = assignment(1);
        when(assignments.findByIdAndMatchId(sentOff.getId(), matchId)).thenReturn(Optional.of(sentOff));
        when(assignments.findByIdAndMatchId(active.getId(), matchId)).thenReturn(Optional.of(active));
        when(cards.existsByMatchIdAndPlayerAssignmentIdAndCardType(
                matchId, sentOff.getId(), MatchCardType.RED)).thenReturn(true);

        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.createGoal(adminId.toString(), matchId, sentOff.getId(), null, false));
        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.createGoal(adminId.toString(), matchId, active.getId(), sentOff.getId(), false));
        assertThrows(LiveMatchService.InvalidCardEventException.class,
                () -> service.createCard(adminId.toString(), matchId, sentOff.getId(), MatchCardType.YELLOW));
        verify(goals, never()).save(any(MatchGoalEvent.class));
        verify(cards, never()).save(any(MatchCardEvent.class));
    }

    @Test
    void twoYellowCardsBlockGoalsAssistsAndNewCards() {
        service.start(adminId.toString(), matchId);
        MatchTeamAssignment sentOff = assignment(1);
        MatchTeamAssignment active = assignment(1);
        when(assignments.findByIdAndMatchId(sentOff.getId(), matchId)).thenReturn(Optional.of(sentOff));
        when(assignments.findByIdAndMatchId(active.getId(), matchId)).thenReturn(Optional.of(active));
        when(cards.countByMatchIdAndPlayerAssignmentIdAndCardType(
                matchId, sentOff.getId(), MatchCardType.YELLOW)).thenReturn(2L);

        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.createGoal(adminId.toString(), matchId, sentOff.getId(), null, false));
        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.createGoal(adminId.toString(), matchId, active.getId(), sentOff.getId(), false));
        assertThrows(LiveMatchService.InvalidCardEventException.class,
                () -> service.createCard(adminId.toString(), matchId, sentOff.getId(), MatchCardType.RED));
        verify(goals, never()).save(any(MatchGoalEvent.class));
        verify(cards, never()).save(any(MatchCardEvent.class));
    }

    @Test
    void deletesGoalAndDecreasesItsTeamScore() {
        LiveMatchScore teamOne = new LiveMatchScore(matchId, 1);
        teamOne.update(2);
        when(scores.findAllByMatchIdOrderBySideNumberAsc(matchId)).thenReturn(List.of(teamOne));
        when(scores.findByMatchIdAndSideNumber(matchId, 1)).thenReturn(Optional.of(teamOne));
        service.start(adminId.toString(), matchId);
        UUID eventId = UUID.randomUUID();
        MatchGoalEvent event = mock(MatchGoalEvent.class);
        when(event.getSideNumber()).thenReturn(1);
        when(goals.findByIdAndMatchId(eventId, matchId)).thenReturn(Optional.of(event));

        LiveMatchStateResponse state = service.deleteGoal(adminId.toString(), matchId, eventId);

        assertEquals(1, teamOne.getScore());
        assertEquals(1, state.scores().getFirst().score());
        verify(goals).delete(event);
    }

    @Test
    void deletingGoalNeverMakesScoreNegative() {
        LiveMatchScore teamOne = new LiveMatchScore(matchId, 1);
        when(scores.findAllByMatchIdOrderBySideNumberAsc(matchId)).thenReturn(List.of(teamOne));
        when(scores.findByMatchIdAndSideNumber(matchId, 1)).thenReturn(Optional.of(teamOne));
        service.start(adminId.toString(), matchId);
        UUID eventId = UUID.randomUUID();
        MatchGoalEvent event = mock(MatchGoalEvent.class);
        when(event.getSideNumber()).thenReturn(1);
        when(goals.findByIdAndMatchId(eventId, matchId)).thenReturn(Optional.of(event));

        LiveMatchStateResponse state = service.deleteGoal(adminId.toString(), matchId, eventId);

        assertEquals(0, teamOne.getScore());
        assertEquals(0, state.scores().getFirst().score());
    }

    @Test
    void deletesCardAndReturnsUpdatedTimeline() {
        service.start(adminId.toString(), matchId);
        UUID eventId = UUID.randomUUID();
        MatchCardEvent event = mock(MatchCardEvent.class);
        when(cards.findByIdAndMatchId(eventId, matchId)).thenReturn(Optional.of(event));

        LiveMatchStateResponse state = service.deleteCard(adminId.toString(), matchId, eventId);

        assertEquals(List.of(), state.cardEvents());
        verify(cards).delete(event);
    }

    @Test
    void deletionCannotAccessEventFromAnotherMatch() {
        service.start(adminId.toString(), matchId);
        UUID foreignEventId = UUID.randomUUID();

        assertThrows(LiveMatchService.InvalidGoalEventException.class,
                () -> service.deleteGoal(adminId.toString(), matchId, foreignEventId));
        assertThrows(LiveMatchService.InvalidCardEventException.class,
                () -> service.deleteCard(adminId.toString(), matchId, foreignEventId));
        verify(goals, never()).delete(any(MatchGoalEvent.class));
        verify(cards, never()).delete(any(MatchCardEvent.class));
    }

    @Test
    void liveEventActionsRemainBlockedAfterThreeHours() {
        match.start(NOW.minus(LiveMatchService.MAX_MATCH_DURATION));
        UUID assignmentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.createGoal(adminId.toString(), matchId, assignmentId, null, false));
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.createCard(adminId.toString(), matchId, assignmentId, MatchCardType.YELLOW));
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.deleteGoal(adminId.toString(), matchId, eventId));
        assertThrows(InvalidLiveMatchTransitionException.class,
                () -> service.deleteCard(adminId.toString(), matchId, eventId));
    }

    @Test
    void automaticallyFinishesAtExactlyThreeHours() {
        match.start(NOW.minus(LiveMatchService.MAX_MATCH_DURATION));
        when(matches.findById(matchId)).thenReturn(Optional.of(match));

        var state = service.get(adminId.toString(), matchId);

        assertEquals(MatchStatus.FINISHED, state.status());
        assertEquals(NOW, state.finishedAt());
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
