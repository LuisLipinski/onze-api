package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.match.MatchMinimumPlayerDecisionModels.ExtendSignupDeadlineRequest;
import com.onze.api.match.MatchMinimumPlayerDecisionService.PaymentDeadlineReviewRequiredException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchMinimumPlayerDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");

    private FootballMatchRepository matchRepository;
    private GroupMemberRepository memberRepository;
    private MatchCapacityService capacityService;
    private MatchTeamAssignmentRepository teamAssignmentRepository;
    private MatchMinimumPlayerDecisionService service;
    private UUID matchId;
    private UUID groupId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        matchRepository = mock(FootballMatchRepository.class);
        memberRepository = mock(GroupMemberRepository.class);
        capacityService = mock(MatchCapacityService.class);
        teamAssignmentRepository = mock(MatchTeamAssignmentRepository.class);
        service = new MatchMinimumPlayerDecisionService(
                matchRepository,
                memberRepository,
                capacityService,
                teamAssignmentRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        matchId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    void approvePersistsExplicitAuthorizationForOccurrence() {
        FootballMatch match = matchWithDeadlines(
                Instant.parse("2026-09-16T14:00:00Z"),
                Instant.parse("2026-09-16T18:00:00Z"));
        stubManageableMatch(match, 10);

        var response = service.approveBelowMinimum(adminId.toString(), matchId);

        assertTrue(match.isBelowMinimumApproved());
        assertTrue(response.belowMinimumApproved());
        assertFalse(response.decisionRequired());
    }

    @Test
    void extendingSignupReopensDecisionWindowAndKeepsOccurrenceScheduled() {
        FootballMatch match = matchWithDeadlines(
                Instant.parse("2026-09-16T14:00:00Z"),
                Instant.parse("2026-09-16T20:00:00Z"));
        stubManageableMatch(match, 10);

        var response = service.extendSignupDeadline(
                adminId.toString(),
                matchId,
                new ExtendSignupDeadlineRequest(
                        java.time.LocalDate.of(2026, 9, 16),
                        java.time.LocalTime.of(18, 0),
                        null,
                        null));

        assertFalse(response.decisionRequired());
        assertFalse(response.belowMinimumApproved());
        assertTrue(match.isSignupOpen(Instant.parse("2026-09-16T17:00:00Z")));
    }

    @Test
    void extendingPastExistingPaymentDeadlineRequiresExplicitPaymentReview() {
        FootballMatch match = matchWithDeadlines(
                Instant.parse("2026-09-16T14:00:00Z"),
                Instant.parse("2026-09-16T16:00:00Z"));
        stubManageableMatch(match, 10);

        assertThrows(PaymentDeadlineReviewRequiredException.class, () ->
                service.extendSignupDeadline(
                        adminId.toString(),
                        matchId,
                        new ExtendSignupDeadlineRequest(
                                java.time.LocalDate.of(2026, 9, 16),
                                java.time.LocalTime.of(18, 0),
                                null,
                                null)));
    }

    @Test
    void decisionIsOnlyRequiredAfterDeadlineWhileBelowMinimumAndNotApproved() {
        FootballMatch match = matchWithDeadlines(
                Instant.parse("2026-09-16T14:00:00Z"),
                Instant.parse("2026-09-16T20:00:00Z"));

        assertTrue(MatchMinimumPlayerDecisionService.decisionRequired(match, NOW, 10));
        assertFalse(MatchMinimumPlayerDecisionService.decisionRequired(match, NOW, 14));
        match.approveBelowMinimum();
        assertFalse(MatchMinimumPlayerDecisionService.decisionRequired(match, NOW, 10));
    }

    private void stubManageableMatch(FootballMatch match, int occupied) {
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(memberRepository.findByGroupIdAndUserId(groupId, adminId))
                .thenReturn(Optional.of(new GroupMember(groupId, adminId, GroupRole.PRIMARY_ADMIN)));
        when(capacityService.occupiedSpots(match.getId())).thenReturn((long) occupied);
    }

    private FootballMatch matchWithDeadlines(Instant signupDeadline, Instant paymentDeadline) {
        return new FootballMatch(
                groupId,
                null,
                null,
                Instant.parse("2026-09-17T00:00:00Z"),
                "UTC",
                "Quadra",
                14,
                MatchType.INTERNAL,
                2,
                2,
                MatchModality.FUT7,
                14,
                java.math.BigDecimal.TEN,
                "pix@example.com",
                true,
                null,
                Instant.parse("2026-09-15T12:00:00Z"),
                Instant.parse("2026-09-15T12:00:00Z"),
                signupDeadline,
                paymentDeadline,
                adminId);
    }
}
