package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.onze.api.group.Group;
import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupService;
import com.onze.api.match.MatchModels.AttendanceResponse;
import com.onze.api.match.MatchModels.CreateMatchRequest;
import com.onze.api.match.MatchModels.MatchResponse;
import com.onze.api.match.MatchModels.PlayerCreditResponse;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    private final FootballMatchRepository matchRepository;
    private final MatchSeriesRepository seriesRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final MatchNotificationQueue notificationQueue;
    private final PlayerCreditService playerCreditService;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MatchService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchNotificationQueue notificationQueue,
            PlayerCreditService playerCreditService,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationQueue = notificationQueue;
        this.playerCreditService = playerCreditService;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public MatchResponse create(String authenticatedUserId, UUID groupId, CreateMatchRequest request) {
        UUID userId = parseUserId(authenticatedUserId);
        Group group = requireGroup(groupId);
        GroupMember membership = requireMembership(groupId, userId);
        requireManagePermission(membership);

        ZoneId zoneId = parseZoneId(request.timeZone());
        Instant startsAt = request.date().atTime(request.startTime()).atZone(zoneId).toInstant();
        Instant now = clock.instant();
        if (!startsAt.isAfter(now)) {
            throw new MatchMustBeInFutureException();
        }

        String venue = request.venue().trim();
        String notes = normalizeOptional(request.notes());
        PaymentConfiguration payment = resolvePaymentConfiguration(group, request);
        FootballMatch match;

        if (request.recurrence() == MatchRecurrence.WEEKLY) {
            MatchSeries series = seriesRepository.save(new MatchSeries(
                    groupId,
                    userId,
                    zoneId.getId(),
                    venue,
                    request.maxPlayers(),
                    payment.amount(),
                    payment.pixKey(),
                    notes));
            match = matchRepository.save(new FootballMatch(
                    groupId,
                    series.getId(),
                    1,
                    startsAt,
                    zoneId.getId(),
                    venue,
                    request.maxPlayers(),
                    payment.amount(),
                    payment.pixKey(),
                    notes,
                    now,
                    now,
                    userId));
            matchRepository.save(MatchRecurrenceSupport.nextOccurrence(match, series));
        } else {
            match = matchRepository.save(new FootballMatch(
                    groupId,
                    null,
                    null,
                    startsAt,
                    zoneId.getId(),
                    venue,
                    request.maxPlayers(),
                    payment.amount(),
                    payment.pixKey(),
                    notes,
                    now,
                    now,
                    userId));
        }

        notificationQueue.enqueue(
                match.getId(),
                null,
                MatchNotificationType.MATCH_CREATED,
                "match:" + match.getId() + ":created",
                now);
        playerCreditService.reserveAvailableCreditsForGroup(groupId, now);
        return toResponse(match, group, membership, now);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listUpcoming(String authenticatedUserId) {
        UUID userId = parseUserId(authenticatedUserId);
        List<GroupMember> memberships = groupMemberRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Instant now = clock.instant();
        List<UUID> groupIds = memberships.stream().map(GroupMember::getGroupId).toList();
        return matchRepository
                .findAllByGroupIdInAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                        groupIds,
                        MatchStatus.SCHEDULED,
                        now)
                .stream()
                .map(match -> {
                    Group group = requireGroup(match.getGroupId());
                    GroupMember membership = memberships.stream()
                            .filter(item -> item.getGroupId().equals(match.getGroupId()))
                            .findFirst()
                            .orElseThrow(GroupService.GroupAccessDeniedException::new);
                    return toResponse(match, group, membership, now);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listForGroup(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        Group group = requireGroup(groupId);
        GroupMember membership = requireMembership(groupId, userId);
        Instant now = clock.instant();

        List<FootballMatch> matches = new ArrayList<>(matchRepository
                .findAllByGroupIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                        groupId,
                        MatchStatus.SCHEDULED,
                        now));
        if (membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) {
            Set<UUID> scheduledIds = matches.stream()
                    .map(FootballMatch::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<UUID> cancelledWithOpenSettlementIds = attendanceRepository
                    .findAllByPaymentSettlementStatusIn(List.of(
                            PaymentSettlementStatus.REVIEW_REQUIRED,
                            PaymentSettlementStatus.PENDING))
                    .stream()
                    .map(MatchAttendance::getMatchId)
                    .distinct()
                    .filter(matchId -> !scheduledIds.contains(matchId))
                    .toList();
            matchRepository.findAllById(cancelledWithOpenSettlementIds)
                    .stream()
                    .filter(match -> match.getGroupId().equals(groupId))
                    .filter(match -> match.getStatus() == MatchStatus.CANCELLED)
                    .forEach(matches::add);
        }

        return matches.stream()
                .sorted(Comparator.comparing(FootballMatch::getStartsAt))
                .map(match -> toResponse(match, group, membership, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlayerCreditResponse> listPlayerCredits(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        requireGroup(groupId);
        GroupMember membership = requireMembership(groupId, userId);
        boolean canManage = membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES);

        return playerCreditService.listForGroup(groupId, clock.instant())
                .stream()
                .filter(credit -> canManage || credit.userId().equals(userId))
                .map(credit -> {
                    User user = userRepository.findById(credit.userId()).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    return new PlayerCreditResponse(
                            credit.userId(),
                            user.getDisplayName(),
                            credit.availableAmount(),
                            credit.allocatedAmount(),
                            credit.allocationStatus(),
                            credit.allocatedMatchId(),
                            credit.allocatedMatchStartsAt(),
                            credit.userId().equals(userId));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse get(String authenticatedUserId, UUID matchId) {
        UUID userId = parseUserId(authenticatedUserId);
        FootballMatch match = requireMatch(matchId);
        Group group = requireGroup(match.getGroupId());
        GroupMember membership = requireMembership(match.getGroupId(), userId);
        return toResponse(match, group, membership, clock.instant());
    }

    @Transactional
    public MatchResponse updateAttendance(
            String authenticatedUserId,
            UUID matchId,
            AttendanceStatus requestedStatus) {
        UUID userId = parseUserId(authenticatedUserId);
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        Group group = requireGroup(match.getGroupId());
        GroupMember membership = requireMembership(match.getGroupId(), userId);
        Instant now = clock.instant();

        requireOpenMatch(match, now);

        MatchAttendance attendance = attendanceRepository.findByMatchIdAndUserId(matchId, userId)
                .orElse(null);
        boolean joining = requestedStatus == AttendanceStatus.GOING
                && (attendance == null || attendance.getStatus() != AttendanceStatus.GOING);
        boolean withdrawing = requestedStatus == AttendanceStatus.NOT_GOING
                && attendance != null
                && attendance.getStatus() != AttendanceStatus.NOT_GOING;
        long goingBefore = attendanceRepository.countByMatchIdAndStatus(matchId, AttendanceStatus.GOING);
        if (joining && goingBefore >= match.getMaxPlayers()) {
            throw new MatchFullException();
        }

        boolean creditReleased = false;
        if (attendance == null) {
            attendance = attendanceRepository.save(new MatchAttendance(
                    matchId,
                    userId,
                    requestedStatus,
                    match.getPaymentAmount()));
        } else {
            if (withdrawing) {
                creditReleased = playerCreditService.releaseReservation(
                        match.getGroupId(),
                        attendance,
                        now);
            }
            attendance.changeStatus(requestedStatus, match.getPaymentAmount(), now);
        }

        if (joining) {
            playerCreditService.reserveForNextMatch(match.getGroupId(), userId, now);
            playerCreditService.consumeReservation(match.getGroupId(), attendance, now);
        } else if (withdrawing && creditReleased) {
            if (!isSettlementOpen(attendance)
                    && attendance.getCashAmountDue().signum() == 0) {
                attendance.markAutomaticCreditReturn(now);
            }
            playerCreditService.reserveForNextMatch(match.getGroupId(), userId, now);
        }

        if (withdrawing && isSettlementOpen(attendance)) {
            enqueueForManagers(
                    match,
                    MatchNotificationType.PAYMENT_SETTLEMENT_REQUIRED,
                    "payment-settlement-required:" + userId + ":" + now.toEpochMilli(),
                    now);
        }

        if (joining && goingBefore + 1 == match.getMaxPlayers()) {
            notificationQueue.enqueue(
                    match.getId(),
                    null,
                    MatchNotificationType.TEAM_FULL,
                    "match:" + match.getId() + ":team-full:" + now.toEpochMilli(),
                    now);
        }

        return toResponse(match, group, membership, now);
    }

    @Transactional
    public MatchResponse reportPayment(String authenticatedUserId, UUID matchId) {
        UUID userId = parseUserId(authenticatedUserId);
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        Group group = requireGroup(match.getGroupId());
        GroupMember membership = requireMembership(match.getGroupId(), userId);
        Instant now = clock.instant();
        requireOpenMatch(match, now);
        requirePayment(match);

        MatchAttendance attendance = attendanceRepository.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(PaymentRequiresAttendanceException::new);
        if (attendance.getStatus() != AttendanceStatus.GOING) {
            throw new PaymentRequiresAttendanceException();
        }

        boolean changed = attendance.getPaymentStatus() == PaymentStatus.PENDING;
        attendance.reportPayment(now);
        if (changed) {
            enqueueForManagers(
                    match,
                    MatchNotificationType.PAYMENT_REPORTED,
                    "payment-reported:" + userId,
                    now);
        }

        return toResponse(match, group, membership, now);
    }

    @Transactional
    public MatchResponse resolvePaymentSettlement(
            String authenticatedUserId,
            UUID matchId,
            UUID playerUserId,
            PaymentSettlementResolution resolution) {
        return resolvePaymentSettlements(
                authenticatedUserId,
                matchId,
                List.of(playerUserId),
                resolution);
    }

    @Transactional
    public MatchResponse resolvePaymentSettlements(
            String authenticatedUserId,
            UUID matchId,
            List<UUID> playerUserIds,
            PaymentSettlementResolution resolution) {
        UUID adminUserId = parseUserId(authenticatedUserId);
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        Group group = requireGroup(match.getGroupId());
        GroupMember membership = requireMembership(match.getGroupId(), adminUserId);
        requireManagePermission(membership);
        requirePayment(match);

        List<MatchAttendance> settlements = playerUserIds.stream()
                .distinct()
                .map(playerUserId -> attendanceRepository.findByMatchIdAndUserId(matchId, playerUserId)
                        .orElseThrow(PaymentSettlementNotOpenException::new))
                .toList();
        for (MatchAttendance attendance : settlements) {
            boolean eligibleAttendance = match.getStatus() == MatchStatus.CANCELLED
                    || attendance.getStatus() == AttendanceStatus.NOT_GOING;
            if (!eligibleAttendance || !isSettlementOpen(attendance)) {
                throw new PaymentSettlementNotOpenException();
            }
            if (attendance.getPaymentSettlementStatus() == PaymentSettlementStatus.PENDING
                    && resolution == PaymentSettlementResolution.NOT_RECEIVED) {
                throw new InvalidPaymentSettlementResolutionException();
            }
        }

        Instant now = clock.instant();
        for (MatchAttendance attendance : settlements) {
            BigDecimal settlementAmount = attendance.settlementAmount();
            try {
                attendance.resolveSettlement(resolution, settlementAmount, now);
            } catch (IllegalArgumentException exception) {
                throw new InvalidPaymentSettlementResolutionException();
            }
            if (resolution == PaymentSettlementResolution.CREDITED
                    && settlementAmount.signum() > 0) {
                playerCreditService.addCredit(
                        match.getGroupId(),
                        attendance.getUserId(),
                        settlementAmount,
                        now);
            }
            notificationQueue.enqueue(
                    match.getId(),
                    attendance.getUserId(),
                    MatchNotificationType.PAYMENT_SETTLEMENT_RESOLVED,
                    "match:" + match.getId() + ":payment-settlement-resolved:"
                            + attendance.getUserId() + ":" + now.toEpochMilli(),
                    now);
        }
        return toResponse(match, group, membership, now);
    }

    @Transactional
    public MatchResponse confirmPayment(
            String authenticatedUserId,
            UUID matchId,
            UUID playerUserId) {
        UUID adminUserId = parseUserId(authenticatedUserId);
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        Group group = requireGroup(match.getGroupId());
        GroupMember membership = requireMembership(match.getGroupId(), adminUserId);
        requireManagePermission(membership);
        Instant now = clock.instant();
        requireOpenMatch(match, now);
        requirePayment(match);

        MatchAttendance attendance = attendanceRepository.findByMatchIdAndUserId(matchId, playerUserId)
                .orElseThrow(PaymentRequiresAttendanceException::new);
        if (attendance.getStatus() != AttendanceStatus.GOING
                || attendance.getPaymentStatus() == null) {
            throw new PaymentRequiresAttendanceException();
        }

        boolean changed = attendance.getPaymentStatus() != PaymentStatus.PAID;
        attendance.confirmPayment(now);
        if (changed) {
            notificationQueue.enqueue(
                    match.getId(),
                    playerUserId,
                    MatchNotificationType.PAYMENT_CONFIRMED,
                    "match:" + match.getId() + ":payment-confirmed:" + playerUserId,
                    now);
        }

        return toResponse(match, group, membership, now);
    }

    @Transactional
    public void cancelOccurrence(String authenticatedUserId, UUID matchId) {
        UUID userId = parseUserId(authenticatedUserId);
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        GroupMember membership = requireMembership(match.getGroupId(), userId);
        requireManagePermission(membership);
        Instant now = clock.instant();
        if (!match.getStartsAt().isAfter(now)) {
            throw new MatchAlreadyStartedException();
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            return;
        }

        match.cancel();
        generateNextAfterCancelledLastOccurrence(match);
        prepareCancelledMatches(List.of(match), now);
        notificationQueue.enqueue(
                match.getId(),
                null,
                MatchNotificationType.MATCH_CANCELLED,
                "match:" + match.getId() + ":cancelled",
                now);
    }

    @Transactional
    public void endSeries(String authenticatedUserId, UUID seriesId) {
        UUID userId = parseUserId(authenticatedUserId);
        MatchSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(MatchSeriesNotFoundException::new);
        GroupMember membership = requireMembership(series.getGroupId(), userId);
        requireManagePermission(membership);
        if (!series.isActive()) {
            return;
        }

        series.deactivate();
        Instant now = clock.instant();
        List<FootballMatch> futureMatches = matchRepository
                .findAllBySeriesIdAndStatusAndStartsAtAfter(
                        seriesId,
                        MatchStatus.SCHEDULED,
                        now)
                .stream()
                .sorted(Comparator.comparing(FootballMatch::getStartsAt))
                .toList();
        futureMatches.forEach(FootballMatch::cancel);
        prepareCancelledMatches(futureMatches, now);
        if (!futureMatches.isEmpty()) {
            notificationQueue.enqueue(
                    futureMatches.get(0).getId(),
                    null,
                    MatchNotificationType.SERIES_CANCELLED,
                    "series:" + seriesId + ":cancelled",
                    now);
        }
    }

    private void prepareCancelledMatches(List<FootballMatch> matches, Instant now) {
        if (matches.isEmpty()) {
            return;
        }
        Set<UUID> usersWithReleasedCredit = new HashSet<>();
        for (FootballMatch match : matches) {
            for (MatchAttendance attendance : attendanceRepository
                    .findAllByMatchIdOrderByCreatedAtAsc(match.getId())) {
                boolean creditReleased = playerCreditService.releaseReservation(
                        match.getGroupId(),
                        attendance,
                        now);
                attendance.prepareCancellationSettlement(now);
                if (creditReleased
                        && !isSettlementOpen(attendance)
                        && attendance.getCashAmountDue().signum() == 0) {
                    attendance.markAutomaticCreditReturn(now);
                }
                if (creditReleased) {
                    usersWithReleasedCredit.add(attendance.getUserId());
                }
                if (isSettlementOpen(attendance)) {
                    enqueueForManagers(
                            match,
                            MatchNotificationType.PAYMENT_SETTLEMENT_REQUIRED,
                            "cancelled-payment-settlement-required:"
                                    + attendance.getUserId() + ":" + now.toEpochMilli(),
                            now);
                }
            }
        }

        UUID groupId = matches.get(0).getGroupId();
        for (UUID userId : usersWithReleasedCredit) {
            playerCreditService.reserveForNextMatch(groupId, userId, now);
        }
    }

    private void generateNextAfterCancelledLastOccurrence(FootballMatch cancelled) {
        if (cancelled.getSeriesId() == null) {
            return;
        }
        MatchSeries series = seriesRepository.findById(cancelled.getSeriesId()).orElse(null);
        if (series == null || !series.isActive()) {
            return;
        }
        FootballMatch latest = matchRepository
                .findFirstBySeriesIdOrderByOccurrenceNumberDesc(series.getId())
                .orElse(cancelled);
        if (latest.getId().equals(cancelled.getId())) {
            matchRepository.save(MatchRecurrenceSupport.nextOccurrence(cancelled, series));
        }
    }

    private MatchResponse toResponse(
            FootballMatch match,
            Group group,
            GroupMember membership,
            Instant now) {
        List<MatchAttendance> storedAttendances = attendanceRepository
                .findAllByMatchIdOrderByCreatedAtAsc(match.getId());
        List<AttendanceResponse> attendances = new ArrayList<>(storedAttendances.size());
        AttendanceStatus myAttendance = null;
        PaymentStatus myPaymentStatus = null;
        PaymentSettlementStatus myPaymentSettlementStatus = null;
        BigDecimal myCreditAppliedAmount = null;
        BigDecimal myRemainingPaymentAmount = null;
        CreditAllocationStatus myCreditAllocationStatus = null;
        int goingCount = 0;
        int notGoingCount = 0;
        boolean canManage = membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES);

        for (MatchAttendance attendance : storedAttendances) {
            User user = userRepository.findById(attendance.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            if (attendance.getStatus() == AttendanceStatus.GOING) {
                goingCount++;
            } else if (attendance.getStatus() == AttendanceStatus.NOT_GOING) {
                notGoingCount++;
            }
            boolean currentUser = attendance.getUserId().equals(membership.getUserId());
            BigDecimal remainingPaymentAmount = remainingPaymentAmount(attendance);
            CreditAllocationStatus creditAllocationStatus = creditAllocationStatus(attendance);
            if (currentUser) {
                myAttendance = attendance.getStatus();
                myPaymentStatus = attendance.getPaymentStatus();
                myPaymentSettlementStatus = attendance.getPaymentSettlementStatus();
                myCreditAppliedAmount = attendance.getCreditAppliedAmount();
                myRemainingPaymentAmount = remainingPaymentAmount;
                myCreditAllocationStatus = creditAllocationStatus;
            }
            boolean financialDetailsVisible = currentUser || canManage;
            attendances.add(new AttendanceResponse(
                    attendance.getUserId(),
                    user.getDisplayName(),
                    attendance.getStatus(),
                    financialDetailsVisible ? attendance.getPaymentStatus() : null,
                    financialDetailsVisible ? attendance.getPaymentSettlementStatus() : null,
                    financialDetailsVisible ? attendance.getCreditAppliedAmount() : null,
                    financialDetailsVisible ? remainingPaymentAmount : null,
                    financialDetailsVisible ? creditAllocationStatus : null,
                    currentUser));
        }

        boolean seriesActive = match.getSeriesId() != null
                && seriesRepository.findById(match.getSeriesId())
                        .map(MatchSeries::isActive)
                        .orElse(false);
        return new MatchResponse(
                match.getId(),
                match.getGroupId(),
                group.getName(),
                match.getSeriesId(),
                match.getSeriesId() == null ? MatchRecurrence.NONE : MatchRecurrence.WEEKLY,
                seriesActive,
                match.getStartsAt(),
                match.getTimeZone(),
                match.getVenue(),
                match.getMaxPlayers(),
                match.isPaymentRequired(),
                match.getPaymentAmount(),
                match.getPixKey(),
                match.getNotes(),
                match.getStatus(),
                match.getAttendanceOpensAt(),
                match.isAttendanceOpen(now),
                myAttendance,
                myPaymentStatus,
                myPaymentSettlementStatus,
                myCreditAppliedAmount,
                myRemainingPaymentAmount,
                myCreditAllocationStatus,
                goingCount,
                notGoingCount,
                List.copyOf(attendances),
                canManage);
    }

    private BigDecimal remainingPaymentAmount(MatchAttendance attendance) {
        return attendance.getCashAmountDue()
                .subtract(attendance.getCashPaidAmount())
                .max(BigDecimal.ZERO);
    }

    private CreditAllocationStatus creditAllocationStatus(MatchAttendance attendance) {
        if (!attendance.hasActiveCredit()) {
            return null;
        }
        return attendance.isCreditConsumed()
                ? CreditAllocationStatus.APPLIED
                : CreditAllocationStatus.RESERVED;
    }

    private boolean isSettlementOpen(MatchAttendance attendance) {
        return attendance.getPaymentSettlementStatus() == PaymentSettlementStatus.REVIEW_REQUIRED
                || attendance.getPaymentSettlementStatus() == PaymentSettlementStatus.PENDING;
    }

    private void enqueueForManagers(
            FootballMatch match,
            MatchNotificationType notificationType,
            String eventKey,
            Instant now) {
        for (GroupMember admin : groupMemberRepository
                .findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId())) {
            if (!admin.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) {
                continue;
            }
            notificationQueue.enqueue(
                    match.getId(),
                    admin.getUserId(),
                    notificationType,
                    "match:" + match.getId() + ":" + eventKey + ":admin:" + admin.getUserId(),
                    now);
        }
    }

    private PaymentConfiguration resolvePaymentConfiguration(
            Group group,
            CreateMatchRequest request) {
        if (Boolean.FALSE.equals(request.paymentRequired())) {
            return PaymentConfiguration.none();
        }

        BigDecimal amount = request.paymentAmount() != null
                ? request.paymentAmount()
                : group.getDefaultPaymentAmount();
        String requestedPixKey = normalizeOptional(request.pixKey());
        String pixKey = requestedPixKey != null ? requestedPixKey : group.getDefaultPixKey();
        boolean required = Boolean.TRUE.equals(request.paymentRequired())
                || (request.paymentRequired() == null && (amount != null || pixKey != null));
        if (!required) {
            return PaymentConfiguration.none();
        }
        if (amount == null || pixKey == null) {
            throw new InvalidPaymentConfigurationException();
        }
        return new PaymentConfiguration(amount, pixKey);
    }

    private void requireOpenMatch(FootballMatch match, Instant now) {
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new MatchCancelledException();
        }
        if (!match.isAttendanceOpen(now)) {
            throw new AttendanceClosedException();
        }
    }

    private void requirePayment(FootballMatch match) {
        if (!match.isPaymentRequired()) {
            throw new PaymentNotRequiredException();
        }
    }

    private Group requireGroup(UUID groupId) {
        return groupRepository.findById(groupId).orElseThrow(GroupService.GroupNotFoundException::new);
    }

    private FootballMatch requireMatch(UUID matchId) {
        return matchRepository.findById(matchId).orElseThrow(MatchNotFoundException::new);
    }

    private GroupMember requireMembership(UUID groupId, UUID userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);
    }

    private void requireManagePermission(GroupMember membership) {
        if (!membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) {
            throw new GroupService.GroupAccessDeniedException();
        }
    }

    private ZoneId parseZoneId(String value) {
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new InvalidTimeZoneException();
        }
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record PaymentConfiguration(BigDecimal amount, String pixKey) {
        private static PaymentConfiguration none() {
            return new PaymentConfiguration(null, null);
        }
    }

    public static final class MatchNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MatchSeriesNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MatchMustBeInFutureException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class InvalidTimeZoneException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class InvalidPaymentConfigurationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class AttendanceClosedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MatchCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MatchFullException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MatchAlreadyStartedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PaymentNotRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PaymentRequiresAttendanceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PaymentSettlementNotOpenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class InvalidPaymentSettlementResolutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
