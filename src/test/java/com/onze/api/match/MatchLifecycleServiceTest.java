package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchLifecycleServiceTest {

    @Test
    void shouldScheduleAttendanceAndPaymentRemindersAtNine() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        FootballMatchRepository matchRepository = mock(FootballMatchRepository.class);
        MatchSeriesRepository seriesRepository = mock(MatchSeriesRepository.class);
        MatchAttendanceRepository attendanceRepository = mock(MatchAttendanceRepository.class);
        GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        MatchNotificationQueue notificationQueue = mock(MatchNotificationQueue.class);
        MatchLifecycleService service = new MatchLifecycleService(
                matchRepository,
                seriesRepository,
                attendanceRepository,
                groupMemberRepository,
                notificationQueue,
                clock);

        UUID matchId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID unansweredUserId = UUID.randomUUID();
        UUID unpaidUserId = UUID.randomUUID();
        FootballMatch match = mock(FootballMatch.class);
        when(match.getId()).thenReturn(matchId);
        when(match.getGroupId()).thenReturn(groupId);
        when(match.getTimeZone()).thenReturn("America/Sao_Paulo");
        when(match.getStartsAt()).thenReturn(Instant.parse("2026-09-03T23:00:00Z"));
        when(match.getAttendanceOpenedAt()).thenReturn(Instant.parse("2026-08-30T12:00:00Z"));
        when(matchRepository.findAllByStatusAndStartsAtAfterOrderByStartsAtAsc(
                MatchStatus.SCHEDULED,
                now)).thenReturn(List.of(match));

        GroupMember unansweredMember = mock(GroupMember.class);
        when(unansweredMember.getUserId()).thenReturn(unansweredUserId);
        GroupMember unpaidMember = mock(GroupMember.class);
        when(unpaidMember.getUserId()).thenReturn(unpaidUserId);
        when(groupMemberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId))
                .thenReturn(List.of(unansweredMember, unpaidMember));

        MatchAttendance unpaidAttendance = mock(MatchAttendance.class);
        when(unpaidAttendance.getUserId()).thenReturn(unpaidUserId);
        when(unpaidAttendance.getStatus()).thenReturn(AttendanceStatus.GOING);
        when(unpaidAttendance.getPaymentStatus()).thenReturn(PaymentStatus.PENDING);
        when(unpaidAttendance.getUpdatedAt()).thenReturn(Instant.parse("2026-08-31T12:00:00Z"));
        when(attendanceRepository.findAllByMatchIdOrderByCreatedAtAsc(matchId))
                .thenReturn(List.of(unpaidAttendance));
        when(notificationQueue.enqueue(any(), any(), any(), any(), any())).thenReturn(true);

        assertThat(service.scheduleDueAttendanceReminders()).isEqualTo(2);
        verify(notificationQueue).enqueue(
                eq(matchId),
                eq(unansweredUserId),
                eq(MatchNotificationType.ATTENDANCE_REMINDER),
                any(),
                eq(now));
        verify(notificationQueue).enqueue(
                eq(matchId),
                eq(unpaidUserId),
                eq(MatchNotificationType.PAYMENT_REMINDER),
                any(),
                eq(now));
    }

    @Test
    void shouldReplacePaymentReminderWithTomorrowReminder() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        FootballMatchRepository matchRepository = mock(FootballMatchRepository.class);
        MatchSeriesRepository seriesRepository = mock(MatchSeriesRepository.class);
        MatchAttendanceRepository attendanceRepository = mock(MatchAttendanceRepository.class);
        GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        MatchNotificationQueue notificationQueue = mock(MatchNotificationQueue.class);
        MatchLifecycleService service = new MatchLifecycleService(
                matchRepository,
                seriesRepository,
                attendanceRepository,
                groupMemberRepository,
                notificationQueue,
                clock);

        UUID matchId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID playerUserId = UUID.randomUUID();
        FootballMatch match = mock(FootballMatch.class);
        when(match.getId()).thenReturn(matchId);
        when(match.getGroupId()).thenReturn(groupId);
        when(match.getTimeZone()).thenReturn("America/Sao_Paulo");
        when(match.getStartsAt()).thenReturn(Instant.parse("2026-09-02T23:00:00Z"));
        when(match.getAttendanceOpenedAt()).thenReturn(Instant.parse("2026-08-30T12:00:00Z"));
        when(matchRepository.findAllByStatusAndStartsAtAfterOrderByStartsAtAsc(
                MatchStatus.SCHEDULED,
                now)).thenReturn(List.of(match));

        GroupMember player = mock(GroupMember.class);
        when(player.getUserId()).thenReturn(playerUserId);
        when(groupMemberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId))
                .thenReturn(List.of(player));

        MatchAttendance attendance = mock(MatchAttendance.class);
        when(attendance.getUserId()).thenReturn(playerUserId);
        when(attendance.getStatus()).thenReturn(AttendanceStatus.GOING);
        when(attendance.getPaymentStatus()).thenReturn(PaymentStatus.PENDING);
        when(attendanceRepository.findAllByMatchIdOrderByCreatedAtAsc(matchId))
                .thenReturn(List.of(attendance));
        when(notificationQueue.enqueue(any(), any(), any(), any(), any())).thenReturn(true);

        assertThat(service.scheduleDueAttendanceReminders()).isEqualTo(1);
        verify(notificationQueue).enqueue(
                eq(matchId),
                eq(playerUserId),
                eq(MatchNotificationType.MATCH_TOMORROW),
                any(),
                eq(now));
    }
}
