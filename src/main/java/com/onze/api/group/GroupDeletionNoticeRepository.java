package com.onze.api.group;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.match.MatchNotificationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface GroupDeletionNoticeRepository extends JpaRepository<GroupDeletionNotice, UUID> {

    List<GroupDeletionNotice> findTop50ByRecipientUserIdAndAcknowledgedAtIsNullOrderByCreatedAtAsc(
            UUID recipientUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GroupDeletionNotice> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GroupDeletionNotice> findTop25ByPushStatusAndNextPushAttemptAtLessThanEqualOrderByCreatedAtAsc(
            MatchNotificationStatus pushStatus, Instant now);
}
