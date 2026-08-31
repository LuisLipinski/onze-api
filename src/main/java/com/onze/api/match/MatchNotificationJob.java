package com.onze.api.match;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "match_notification_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_notification_job_deduplication",
                columnNames = "deduplication_key"))
public class MatchNotificationJob {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 48)
    private MatchNotificationType notificationType;

    @Column(name = "deduplication_key", nullable = false, unique = true, length = 255)
    private String deduplicationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MatchNotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected MatchNotificationJob() {
    }

    public MatchNotificationJob(
            UUID matchId,
            UUID recipientUserId,
            MatchNotificationType notificationType,
            String deduplicationKey,
            Instant scheduledAt) {
        this.matchId = matchId;
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.deduplicationKey = deduplicationKey;
        this.status = MatchNotificationStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = scheduledAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public MatchNotificationType getNotificationType() {
        return notificationType;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public MatchNotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void markSent(Instant now) {
        status = MatchNotificationStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    public void markFailedAttempt(Instant now, String error) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= MAX_ATTEMPTS) {
            status = MatchNotificationStatus.FAILED;
            return;
        }
        long delayMinutes = 1L << Math.min(attempts - 1, 5);
        nextAttemptAt = now.plus(Duration.ofMinutes(delayMinutes));
    }
}
