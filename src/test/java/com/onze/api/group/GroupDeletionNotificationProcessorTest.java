package com.onze.api.group;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.onze.api.match.ExpoPushNotificationSender;
import com.onze.api.match.MatchNotificationStatus;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupDeletionNotificationProcessorTest {
    private static final Instant NOW = Instant.parse("2026-10-17T01:00:00Z");

    @Test
    void sendsFromSnapshotWithoutNeedingTheDeletedGroupOrItsMatches() {
        GroupDeletionNotice notice = notice();
        GroupDeletionNoticeRepository repository = mock(GroupDeletionNoticeRepository.class);
        ExpoPushNotificationSender sender = mock(ExpoPushNotificationSender.class);
        when(repository.findTop25ByPushStatusAndNextPushAttemptAtLessThanEqualOrderByCreatedAtAsc(
                MatchNotificationStatus.PENDING, NOW)).thenReturn(List.of(notice));
        var processor = new GroupDeletionNotificationProcessor(repository, sender, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(processor.processPending()).isEqualTo(1);
        verify(sender).sendGroupDeleted(notice.getId(), notice.getGroupId(), "Grupo antigo", notice.getRecipientUserId());
        assertThat(notice.getPushStatus()).isEqualTo(MatchNotificationStatus.SENT);
        assertThat(notice.getAcknowledgedAt()).isNull();
    }

    @Test
    void failedPushRetriesWithBackoffAndNeverRemovesTheUnreadNotice() {
        GroupDeletionNotice notice = notice();
        GroupDeletionNoticeRepository repository = mock(GroupDeletionNoticeRepository.class);
        ExpoPushNotificationSender sender = mock(ExpoPushNotificationSender.class);
        when(repository.findTop25ByPushStatusAndNextPushAttemptAtLessThanEqualOrderByCreatedAtAsc(
                MatchNotificationStatus.PENDING, NOW)).thenReturn(List.of(notice));
        doThrow(new IllegalStateException("Expo unavailable"))
                .when(sender).sendGroupDeleted(any(), any(), anyString(), any());
        var processor = new GroupDeletionNotificationProcessor(repository, sender, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(processor.processPending()).isZero();
        assertThat(notice.getPushStatus()).isEqualTo(MatchNotificationStatus.PENDING);
        assertThat(notice.getNextPushAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        for (int attempt = 0; attempt < 4; attempt++) processor.processPending();
        assertThat(notice.getPushStatus()).isEqualTo(MatchNotificationStatus.FAILED);
        assertThat(notice.getPushAttempts()).isEqualTo(5);
        assertThat(notice.getAcknowledgedAt()).isNull();
    }

    private GroupDeletionNotice notice() {
        GroupDeletionNotice notice = new GroupDeletionNotice(UUID.randomUUID(), "Grupo antigo", UUID.randomUUID(), NOW);
        ReflectionTestUtils.setField(notice, "id", UUID.randomUUID());
        return notice;
    }
}
