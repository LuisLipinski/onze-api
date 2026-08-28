package com.onze.api.match;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final MatchNotificationJobRepository notificationJobRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MatchService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchNotificationJobRepository notificationJobRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationJobRepository = notificationJobRepository;
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
        FootballMatch match;

        if (request.recurrence() == MatchRecurrence.WEEKLY) {
            MatchSeries series = seriesRepository.save(new MatchSeries(
                    groupId,
                    userId,
                    zoneId.getId(),
                    venue,
                    request.maxPlayers(),
                    notes));
            match = matchRepository.save(new FootballMatch(
                    groupId,
                    series.getId(),
                    1,
                    startsAt,
                    zoneId.getId(),
                    venue,
                    request.maxPlayers(),
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
                    notes,
                    now,
                    now,
                    userId));
        }

        notificationJobRepository.save(new MatchNotificationJob(
                match.getId(),
                MatchNotificationType.MATCH_CREATED,
                now));
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

        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new MatchCancelledException();
        }
        if (!match.isAttendanceOpen(now)) {
            throw new AttendanceClosedException();
        }

        MatchAttendance attendance = attendanceRepository.findByMatchIdAndUserId(matchId, userId)
                .orElse(null);
        boolean joining = requestedStatus == AttendanceStatus.GOING
                && (attendance == null || attendance.getStatus() != AttendanceStatus.GOING);
        if (joining
                && attendanceRepository.countByMatchIdAndStatus(matchId, AttendanceStatus.GOING)
                        >= match.getMaxPlayers()) {
            throw new MatchFullException();
        }

        if (attendance == null) {
            attendanceRepository.save(new MatchAttendance(matchId, userId, requestedStatus));
        } else {
            attendance.changeStatus(requestedStatus);
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
        if (!match.getStartsAt().isAfter(clock.instant())) {
            throw new MatchAlreadyStartedException();
        }

        match.cancel();
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
        matchRepository.findAllBySeriesIdAndStatusAndStartsAtAfter(
                        seriesId,
                        MatchStatus.SCHEDULED,
                        now)
                .forEach(FootballMatch::cancel);
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
        int goingCount = 0;
        int notGoingCount = 0;

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
            }
            attendances.add(new AttendanceResponse(
                    attendance.getUserId(),
                    user.getDisplayName(),
                    attendance.getStatus(),
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
                match.getNotes(),
                match.getStatus(),
                match.getAttendanceOpensAt(),
                match.isAttendanceOpen(now),
                myAttendance,
                goingCount,
                notGoingCount,
                List.copyOf(attendances),
                membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
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
}
