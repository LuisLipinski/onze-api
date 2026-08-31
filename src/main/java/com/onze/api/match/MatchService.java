package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MatchService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchNotificationQueue notificationQueue,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationQueue = notificationQueue;
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

        return matchRepository
                .findAllByGroupIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                        groupId,
                        MatchStatus.SCHEDULED,
                        now)
                .stream()
                .map(match -> toResponse(match, group, membership, now))
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
        boolean leaving = requestedStatus == AttendanceStatus.NOT_GOING
                && attendance != null
                && attendance.getStatus() == AttendanceStatus.GOING;
        PaymentStatus paymentBefore = attendance == null ? null : attendance.getPaymentStatus();
        long goingBefore = attendanceRepository.countByMatchIdAndStatus(matchId, AttendanceStatus.GOING);
        if (joining && goingBefore >= match.getMaxPlayers()) {
            throw new MatchFullException();
        }

        if (attendance == null) {
            attendanceRepository.save(new MatchAttendance(
                    matchId,
                    userId,
                    requestedStatus,
                    match.isPaymentRequired()));
        } else {
            attendance.changeStatus(requestedStatus, match.isPaymentRequired(), now);
        }

        if (leaving && (paymentBefore == PaymentStatus.REPORTED || paymentBefore == PaymentStatus.PAID)) {
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
        UUID adminUserId = parseUserId(authenticatedUserId);
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        Group group = requireGroup(match.getGroupId());
        GroupMember membership = requireMembership(match.getGroupId(), adminUserId);
        requireManagePermission(membership);
        requirePayment(match);

        MatchAttendance attendance = attendanceRepository.findByMatchIdAndUserId(matchId, playerUserId)
                .orElseThrow(PaymentSettlementNotOpenException::new);
        if (attendance.getStatus() != AttendanceStatus.NOT_GOING
                || (attendance.getPaymentSettlementStatus() != PaymentSettlementStatus.REVIEW_REQUIRED
                        && attendance.getPaymentSettlementStatus() != PaymentSettlementStatus.PENDING)) {
            throw new PaymentSettlementNotOpenException();
        }

        Instant now = clock.instant();
        try {
            attendance.resolveSettlement(resolution, now);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPaymentSettlementResolutionException();
        }
        notificationQueue.enqueue(
                match.getId(),
                playerUserId,
                MatchNotificationType.PAYMENT_SETTLEMENT_RESOLVED,
                "match:" + match.getId() + ":payment-settlement-resolved:"
                        + playerUserId + ":" + now.toEpochMilli(),
                now);
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
        notificationQueue.enqueue(
                match.getId(),
                null,
                MatchNotificationType.MATCH_CANCELLED,
                "match:" + match.getId() + ":cancelled",
                now);
        generateNextAfterCancelledLastOccurrence(match);
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
        if (!futureMatches.isEmpty()) {
            notificationQueue.enqueue(
                    futureMatches.get(0).getId(),
                    null,
                    MatchNotificationType.SERIES_CANCELLED,
                    "series:" + seriesId + ":cancelled",
                    now);
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
            } else {
                notGoingCount++;
            }
            boolean currentUser = attendance.getUserId().equals(membership.getUserId());
            if (currentUser) {
                myAttendance = attendance.getStatus();
                myPaymentStatus = attendance.getPaymentStatus();
                myPaymentSettlementStatus = attendance.getPaymentSettlementStatus();
            }
            attendances.add(new AttendanceResponse(
                    attendance.getUserId(),
                    user.getDisplayName(),
                    attendance.getStatus(),
                    currentUser || canManage ? attendance.getPaymentStatus() : null,
                    currentUser || canManage ? attendance.getPaymentSettlementStatus() : null,
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
                goingCount,
                notGoingCount,
                List.copyOf(attendances),
                canManage);
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
