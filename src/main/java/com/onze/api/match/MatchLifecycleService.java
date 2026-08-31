package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchLifecycleService {

    private static final int MAX_CATCH_UP_ROUNDS = 104;
    private static final LocalTime DAILY_REMINDER_TIME = LocalTime.of(9, 0);

    private final FootballMatchRepository matchRepository;
    private final MatchSeriesRepository seriesRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MatchNotificationQueue notificationQueue;
    private final Clock clock;

    public MatchLifecycleService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            MatchAttendanceRepository attendanceRepository,
            GroupMemberRepository groupMemberRepository,
            MatchNotificationQueue notificationQueue,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.attendanceRepository = attendanceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.notificationQueue = notificationQueue;
        this.clock = clock;
    }

    @Transactional
    public int openDueAttendances() {
        Instant now = clock.instant();
        int opened = 0;

        for (int round = 0; round < MAX_CATCH_UP_ROUNDS; round++) {
            List<FootballMatch> dueMatches = matchRepository
                    .findTop50ByStatusAndAttendanceOpenedAtIsNullAndAttendanceOpensAtLessThanEqualOrderByAttendanceOpensAtAsc(
                            MatchStatus.SCHEDULED,
                            now);
            if (dueMatches.isEmpty()) {
                break;
            }

            int openedThisRound = 0;
            for (FootballMatch due : dueMatches) {
                FootballMatch match = matchRepository.findByIdForUpdate(due.getId()).orElse(null);
                if (match == null
                        || match.getStatus() != MatchStatus.SCHEDULED
                        || match.getAttendanceOpenedAt() != null
                        || match.getAttendanceOpensAt().isAfter(now)) {
                    continue;
                }

                match.openAttendance(now);
                if (match.getStartsAt().isAfter(now)) {
                    notificationQueue.enqueue(
                            match.getId(),
                            null,
                            MatchNotificationType.ATTENDANCE_OPENED,
                            "match:" + match.getId() + ":attendance-opened",
                            now);
                }
                generateNextOccurrenceIfNeeded(match);
                opened++;
                openedThisRound++;
            }

            if (openedThisRound == 0) {
                break;
            }
        }

        return opened;
    }

    @Transactional
    public int scheduleDueAttendanceReminders() {
        Instant now = clock.instant();
        int scheduled = 0;

        for (FootballMatch match : matchRepository
                .findAllByStatusAndStartsAtAfterOrderByStartsAtAsc(MatchStatus.SCHEDULED, now)) {
            if (match.getAttendanceOpenedAt() == null) {
                continue;
            }

            ZoneId zoneId = ZoneId.of(match.getTimeZone());
            LocalDate today = now.atZone(zoneId).toLocalDate();
            if (now.atZone(zoneId).toLocalTime().isBefore(DAILY_REMINDER_TIME)
                    || !today.isBefore(match.getStartsAt().atZone(zoneId).toLocalDate())
                    || !today.isAfter(match.getAttendanceOpenedAt().atZone(zoneId).toLocalDate())) {
                continue;
            }

            Map<UUID, MatchAttendance> attendanceByUser = attendanceRepository
                    .findAllByMatchIdOrderByCreatedAtAsc(match.getId())
                    .stream()
                    .collect(Collectors.toMap(MatchAttendance::getUserId, Function.identity()));

            for (GroupMember member : groupMemberRepository
                    .findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId())) {
                MatchAttendance attendance = attendanceByUser.get(member.getUserId());
                if (attendance == null) {
                    String key = "match:" + match.getId()
                            + ":attendance-reminder:"
                            + member.getUserId()
                            + ":"
                            + today;
                    if (notificationQueue.enqueue(
                            match.getId(),
                            member.getUserId(),
                            MatchNotificationType.ATTENDANCE_REMINDER,
                            key,
                            now)) {
                        scheduled++;
                    }
                    continue;
                }

                if (attendance.getStatus() != AttendanceStatus.GOING) {
                    continue;
                }

                LocalDate matchDay = match.getStartsAt().atZone(zoneId).toLocalDate();
                if (today.plusDays(1).equals(matchDay)) {
                    if (notificationQueue.enqueue(
                            match.getId(),
                            member.getUserId(),
                            MatchNotificationType.MATCH_TOMORROW,
                            "match:" + match.getId() + ":tomorrow:"
                                    + member.getUserId() + ":" + today,
                            now)) {
                        scheduled++;
                    }
                } else if (attendance.getPaymentStatus() == PaymentStatus.PENDING
                        && today.isAfter(attendance.getUpdatedAt().atZone(zoneId).toLocalDate())) {
                    if (notificationQueue.enqueue(
                            match.getId(),
                            member.getUserId(),
                            MatchNotificationType.PAYMENT_REMINDER,
                            "match:" + match.getId() + ":payment-reminder:"
                                    + member.getUserId() + ":" + today,
                            now)) {
                        scheduled++;
                    }
                }
            }
        }

        return scheduled;
    }

    private void generateNextOccurrenceIfNeeded(FootballMatch match) {
        if (match.getSeriesId() == null) {
            return;
        }

        MatchSeries series = seriesRepository.findById(match.getSeriesId()).orElse(null);
        if (series == null || !series.isActive()) {
            return;
        }

        FootballMatch latest = matchRepository
                .findFirstBySeriesIdOrderByOccurrenceNumberDesc(series.getId())
                .orElse(match);
        if (!latest.getId().equals(match.getId())) {
            return;
        }

        matchRepository.save(MatchRecurrenceSupport.nextOccurrence(match, series));
    }
}
