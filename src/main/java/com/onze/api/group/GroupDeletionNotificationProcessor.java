package com.onze.api.group;

import java.time.Clock;
import java.time.Instant;

import com.onze.api.match.ExpoPushNotificationSender;
import com.onze.api.match.MatchNotificationStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupDeletionNotificationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupDeletionNotificationProcessor.class);
    private final GroupDeletionNoticeRepository noticeRepository;
    private final ExpoPushNotificationSender pushSender;
    private final Clock clock;

    public GroupDeletionNotificationProcessor(
            GroupDeletionNoticeRepository noticeRepository, ExpoPushNotificationSender pushSender, Clock clock) {
        this.noticeRepository = noticeRepository;
        this.pushSender = pushSender;
        this.clock = clock;
    }

    @Transactional
    public int processPending() {
        Instant now = clock.instant();
        int sent = 0;
        for (GroupDeletionNotice notice : noticeRepository
                .findTop25ByPushStatusAndNextPushAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        MatchNotificationStatus.PENDING, now)) {
            try {
                pushSender.sendGroupDeleted(
                        notice.getId(), notice.getGroupId(), notice.getGroupName(), notice.getRecipientUserId());
                notice.markPushSent();
                sent++;
            } catch (RuntimeException exception) {
                notice.markPushFailed(now);
                LOGGER.warn("Group deletion push failed: noticeId={}, attempt={}, status={}, reason={}",
                        notice.getId(), notice.getPushAttempts(), notice.getPushStatus(),
                        exception.getClass().getSimpleName());
            }
        }
        return sent;
    }
}
