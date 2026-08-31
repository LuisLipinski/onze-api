package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchNotificationProcessor {

    private final MatchNotificationJobRepository notificationJobRepository;
    private final FootballMatchRepository matchRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final ExpoPushNotificationSender pushNotificationSender;
    private final Clock clock;

    public MatchNotificationProcessor(
            MatchNotificationJobRepository notificationJobRepository,
            FootballMatchRepository matchRepository,
            MatchAttendanceRepository attendanceRepository,
            ExpoPushNotificationSender pushNotificationSender,
            Clock clock) {
        this.notificationJobRepository = notificationJobRepository;
        this.matchRepository = matchRepository;
        this.attendanceRepository = attendanceRepository;
        this.pushNotificationSender = pushNotificationSender;
        this.clock = clock;
    }

    @Transactional
    public int processPending() {
        Instant now = clock.instant();
        List<MatchNotificationJob> jobs = notificationJobRepository
                .findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        MatchNotificationStatus.PENDING,
                        now);
        int processed = 0;

        for (MatchNotificationJob job : jobs) {
            FootballMatch match = matchRepository.findById(job.getMatchId()).orElse(null);
            if (shouldSkip(job, match, now)) {
                job.markSent(now);
                processed++;
                continue;
            }

            try {
                pushNotificationSender.send(
                        match,
                        job.getNotificationType(),
                        job.getRecipientUserId());
                job.markSent(now);
                processed++;
            } catch (RuntimeException exception) {
                job.markFailedAttempt(now, exception.getMessage());
            }
        }

        return processed;
    }

    private boolean shouldSkip(MatchNotificationJob job, FootballMatch match, Instant now) {
        if (match == null || !match.getStartsAt().isAfter(now)) {
            return true;
        }

        boolean cancellationNotification = job.getNotificationType() == MatchNotificationType.MATCH_CANCELLED
                || job.getNotificationType() == MatchNotificationType.SERIES_CANCELLED;
        if (match.getStatus() == MatchStatus.CANCELLED && !cancellationNotification) {
            return true;
        }

        if (job.getNotificationType() == MatchNotificationType.TEAM_FULL) {
            return attendanceRepository.countByMatchIdAndStatus(
                    match.getId(),
                    AttendanceStatus.GOING) < match.getMaxPlayers();
        }

        if (job.getNotificationType() != MatchNotificationType.ATTENDANCE_REMINDER
                && job.getNotificationType() != MatchNotificationType.PAYMENT_REMINDER
                && job.getNotificationType() != MatchNotificationType.MATCH_TOMORROW) {
            return false;
        }
        if (job.getRecipientUserId() == null || !match.isAttendanceOpen(now)) {
            return true;
        }
        MatchAttendance attendance = attendanceRepository
                .findByMatchIdAndUserId(match.getId(), job.getRecipientUserId())
                .orElse(null);
        if (job.getNotificationType() == MatchNotificationType.ATTENDANCE_REMINDER) {
            return attendance != null;
        }
        if (attendance == null || attendance.getStatus() != AttendanceStatus.GOING) {
            return true;
        }
        return job.getNotificationType() == MatchNotificationType.PAYMENT_REMINDER
                && attendance.getPaymentStatus() != PaymentStatus.PENDING;
    }
}
