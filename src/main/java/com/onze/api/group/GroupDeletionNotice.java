package com.onze.api.group;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.onze.api.match.MatchNotificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_deletion_notices")
public class GroupDeletionNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 24)
    private MatchNotificationStatus pushStatus;

    @Column(name = "push_attempts", nullable = false)
    private int pushAttempts;

    @Column(name = "next_push_attempt_at", nullable = false)
    private Instant nextPushAttemptAt;

    protected GroupDeletionNotice() {
    }

    public GroupDeletionNotice(UUID groupId, String groupName, UUID recipientUserId, Instant now) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.recipientUserId = recipientUserId;
        this.createdAt = now;
        this.pushStatus = MatchNotificationStatus.PENDING;
        this.nextPushAttemptAt = now;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public MatchNotificationStatus getPushStatus() { return pushStatus; }
    public int getPushAttempts() { return pushAttempts; }
    public Instant getNextPushAttemptAt() { return nextPushAttemptAt; }

    public void acknowledge(Instant now) {
        if (acknowledgedAt == null) acknowledgedAt = now;
    }

    public void markPushSent() {
        pushStatus = MatchNotificationStatus.SENT;
    }

    public void markPushFailed(Instant now) {
        pushAttempts++;
        if (pushAttempts >= 5) {
            pushStatus = MatchNotificationStatus.FAILED;
        } else {
            nextPushAttemptAt = now.plus(Duration.ofMinutes(1L << (pushAttempts - 1)));
        }
    }
}
