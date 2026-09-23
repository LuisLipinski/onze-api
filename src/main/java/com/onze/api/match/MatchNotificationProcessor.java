package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchNotificationProcessor {

    private static final List<MatchNotificationType> LIVE_MATCH_NOTIFICATION_TYPES = List.of(
            MatchNotificationType.LIVE_MATCH_STARTED,
            MatchNotificationType.LIVE_MATCH_GOAL,
            MatchNotificationType.LIVE_MATCH_YELLOW_CARD,
            MatchNotificationType.LIVE_MATCH_SECOND_YELLOW_CARD,
            MatchNotificationType.LIVE_MATCH_RED_CARD,
            MatchNotificationType.LIVE_MATCH_FINISHED);

    private final MatchNotificationJobRepository notificationJobRepository;
    private final FootballMatchRepository matchRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final MatchCapacityService capacityService;
    private final ExpoPushNotificationSender pushNotificationSender;
    private final Clock clock;

    public MatchNotificationProcessor(
            MatchNotificationJobRepository notificationJobRepository,
            FootballMatchRepository matchRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchCapacityService capacityService,
            ExpoPushNotificationSender pushNotificationSender,
            Clock clock) {
        this.notificationJobRepository = notificationJobRepository;
        this.matchRepository = matchRepository;
        this.attendanceRepository = attendanceRepository;
        this.capacityService = capacityService;
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
        return process(jobs, now);
    }

    @Transactional
    public int processPendingLiveMatch() {
        Instant now = clock.instant();
        List<MatchNotificationJob> jobs = notificationJobRepository
                .findTop25ByStatusAndNotificationTypeInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        MatchNotificationStatus.PENDING,
                        LIVE_MATCH_NOTIFICATION_TYPES,
                        now);
        return process(jobs, now);
    }

    private int process(List<MatchNotificationJob> jobs, Instant now) {
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
        if (match == null) {
            return true;
        }

        boolean liveMatchNotification = job.getNotificationType().name().startsWith("LIVE_MATCH_");
        boolean stateIndependentNotification = job.getNotificationType() == MatchNotificationType.MATCH_CANCELLED
                || job.getNotificationType() == MatchNotificationType.SERIES_CANCELLED
                || job.getNotificationType() == MatchNotificationType.PAYMENT_SETTLEMENT_REQUIRED
                || job.getNotificationType() == MatchNotificationType.PAYMENT_SETTLEMENT_RESOLVED
                || job.getNotificationType() == MatchNotificationType.REPLACEMENT_ADDED
                || job.getNotificationType() == MatchNotificationType.REPLACEMENT_FILLED
                || liveMatchNotification;
        if (!match.getStartsAt().isAfter(now) && !stateIndependentNotification) {
            return true;
        }
        if (match.getStatus() == MatchStatus.CANCELLED && !stateIndependentNotification) {
            return true;
        }

        if (liveMatchNotification) {
            if (job.getNotificationType() == MatchNotificationType.LIVE_MATCH_STARTED) {
                return match.getStatus() != MatchStatus.IN_PROGRESS;
            }
            if (job.getNotificationType() == MatchNotificationType.LIVE_MATCH_FINISHED) {
                return match.getStatus() != MatchStatus.FINISHED;
            }
            return match.getStatus() == MatchStatus.SCHEDULED
                    || match.getStatus() == MatchStatus.CANCELLED;
        }

        if (job.getNotificationType() == MatchNotificationType.TEAM_FULL) {
            return !capacityService.isFull(match);
        }

        if (job.getNotificationType() == MatchNotificationType.CREDIT_APPLIED) {
            return job.getRecipientUserId() == null
                    || attendanceRepository
                            .findByMatchIdAndUserId(match.getId(), job.getRecipientUserId())
                            .map(attendance -> !attendance.hasActiveCredit())
                            .orElse(true);
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
            return !match.isSignupOpen(now)
                    || (attendance != null && attendance.getStatus() != AttendanceStatus.PENDING);
        }
        if (attendance == null || attendance.getStatus() != AttendanceStatus.GOING) {
            return true;
        }
        return job.getNotificationType() == MatchNotificationType.PAYMENT_REMINDER
                && (!MatchPaymentPolicy.requiresPayment(match, attendance)
                        || !match.isPaymentOpen(now)
                        || attendance.getPaymentStatus() != PaymentStatus.PENDING);
    }
}
