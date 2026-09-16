package com.onze.api.match;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService;
import com.onze.api.match.MatchMinimumPlayerDecisionModels.ExtendSignupDeadlineRequest;
import com.onze.api.match.MatchMinimumPlayerDecisionModels.MinimumPlayerDecisionResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchMinimumPlayerDecisionService {

    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final MatchCapacityService capacityService;
    private final MatchTeamAssignmentRepository teamAssignmentRepository;
    private final Clock clock;

    public MatchMinimumPlayerDecisionService(
            FootballMatchRepository matchRepository,
            GroupMemberRepository memberRepository,
            MatchCapacityService capacityService,
            MatchTeamAssignmentRepository teamAssignmentRepository,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.capacityService = capacityService;
        this.teamAssignmentRepository = teamAssignmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MinimumPlayerDecisionResponse status(String authenticatedUserId, UUID matchId) {
        FootballMatch match = requireMatch(matchId);
        requireMembership(match.getGroupId(), parseUserId(authenticatedUserId));
        return response(match, clock.instant());
    }

    @Transactional
    public MinimumPlayerDecisionResponse approveBelowMinimum(
            String authenticatedUserId,
            UUID matchId) {
        FootballMatch match = requireMatchForUpdate(matchId);
        GroupMember actor = requireMembership(match.getGroupId(), parseUserId(authenticatedUserId));
        requireManagePermission(actor);
        Instant now = clock.instant();
        requirePendingDecision(match, now);
        match.approveBelowMinimum();
        teamAssignmentRepository.deleteAllByMatchId(matchId);
        return response(match, now);
    }

    @Transactional
    public MinimumPlayerDecisionResponse extendSignupDeadline(
            String authenticatedUserId,
            UUID matchId,
            ExtendSignupDeadlineRequest request) {
        FootballMatch match = requireMatchForUpdate(matchId);
        GroupMember actor = requireMembership(match.getGroupId(), parseUserId(authenticatedUserId));
        requireManagePermission(actor);
        Instant now = clock.instant();
        requirePendingDecision(match, now);

        ZoneId zoneId = parseZoneId(match.getTimeZone());
        Instant signupDeadline = request.signupDeadlineDate()
                .atTime(request.signupDeadlineTime())
                .atZone(zoneId)
                .toInstant();
        if (!signupDeadline.isAfter(now) || !signupDeadline.isBefore(match.getStartsAt())) {
            throw new InvalidSignupDeadlineExtensionException();
        }

        boolean paymentDateProvided = request.paymentDeadlineDate() != null;
        boolean paymentTimeProvided = request.paymentDeadlineTime() != null;
        if (paymentDateProvided != paymentTimeProvided) {
            throw new InvalidSignupDeadlineExtensionException();
        }

        Instant paymentDeadline = match.getPaymentDeadline();
        if (!match.isPaymentRequired()) {
            if (paymentDateProvided) {
                throw new InvalidSignupDeadlineExtensionException();
            }
            paymentDeadline = null;
        } else if (paymentDateProvided) {
            paymentDeadline = request.paymentDeadlineDate()
                    .atTime(request.paymentDeadlineTime())
                    .atZone(zoneId)
                    .toInstant();
            if (!paymentDeadline.isAfter(now)
                    || paymentDeadline.isBefore(signupDeadline)
                    || !paymentDeadline.isBefore(match.getStartsAt())) {
                throw new InvalidSignupDeadlineExtensionException();
            }
        } else if (paymentDeadline == null || paymentDeadline.isBefore(signupDeadline)) {
            throw new PaymentDeadlineReviewRequiredException();
        }

        match.extendSignupDeadline(signupDeadline, paymentDeadline);
        teamAssignmentRepository.deleteAllByMatchId(matchId);
        return response(match, now);
    }

    private MinimumPlayerDecisionResponse response(FootballMatch match, Instant now) {
        int confirmedPlayers = Math.toIntExact(capacityService.occupiedSpots(match.getId()));
        return new MinimumPlayerDecisionResponse(
                match.getId(),
                confirmedPlayers,
                match.getMinimumPlayers(),
                match.getSignupDeadline(),
                match.getPaymentDeadline(),
                decisionRequired(match, now, confirmedPlayers),
                match.isBelowMinimumApproved());
    }

    private void requirePendingDecision(FootballMatch match, Instant now) {
        int confirmedPlayers = Math.toIntExact(capacityService.occupiedSpots(match.getId()));
        if (!decisionRequired(match, now, confirmedPlayers)) {
            throw new MinimumPlayerDecisionNotRequiredException();
        }
    }

    static boolean decisionRequired(FootballMatch match, Instant now, int confirmedPlayers) {
        return match.getStatus() == MatchStatus.SCHEDULED
                && match.getStartsAt().isAfter(now)
                && now.isAfter(match.getSignupDeadline())
                && confirmedPlayers < match.getMinimumPlayers()
                && !match.isBelowMinimumApproved();
    }

    private FootballMatch requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
    }

    private FootballMatch requireMatchForUpdate(UUID matchId) {
        return matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
    }

    private GroupMember requireMembership(UUID groupId, UUID userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);
    }

    private void requireManagePermission(GroupMember member) {
        if (!member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) {
            throw new GroupService.GroupAccessDeniedException();
        }
    }

    private UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
    }

    private ZoneId parseZoneId(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw new InvalidSignupDeadlineExtensionException();
        }
    }

    public static final class MinimumPlayerDecisionNotRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class InvalidSignupDeadlineExtensionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PaymentDeadlineReviewRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
