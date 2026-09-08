package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class MatchNotificationQueue {

    private final MatchNotificationJobRepository notificationJobRepository;

    public MatchNotificationQueue(MatchNotificationJobRepository notificationJobRepository) {
        this.notificationJobRepository = notificationJobRepository;
    }

    public boolean enqueue(
            UUID matchId,
            UUID recipientUserId,
            MatchNotificationType notificationType,
            String deduplicationKey,
            Instant scheduledAt) {
        if (notificationJobRepository.existsByDeduplicationKey(deduplicationKey)) {
            return false;
        }
        notificationJobRepository.save(new MatchNotificationJob(
                matchId,
                recipientUserId,
                notificationType,
                deduplicationKey,
                scheduledAt));
        return true;
    }
}
