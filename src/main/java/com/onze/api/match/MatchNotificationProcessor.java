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
    private final ExpoPushNotificationSender pushNotificationSender;
    private final Clock clock;

    public MatchNotificationProcessor(
            MatchNotificationJobRepository notificationJobRepository,
            FootballMatchRepository matchRepository,
            ExpoPushNotificationSender pushNotificationSender,
            Clock clock) {
        this.notificationJobRepository = notificationJobRepository;
        this.matchRepository = matchRepository;
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
            if (match == null
                    || match.getStatus() == MatchStatus.CANCELLED
                    || !match.getStartsAt().isAfter(now)) {
                job.markSent(now);
                processed++;
                continue;
            }

            try {
                pushNotificationSender.send(match, job.getNotificationType());
                job.markSent(now);
                processed++;
            } catch (RuntimeException exception) {
                job.markFailedAttempt(now, exception.getMessage());
            }
        }

        return processed;
    }
}
