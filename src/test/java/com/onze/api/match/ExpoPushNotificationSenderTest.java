package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.Group;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpoPushNotificationSenderTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private MatchAttendanceRepository attendanceRepository;

    @Mock
    private MatchCapacityService capacityService;

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    private ExpoPushNotificationSender sender;
    private FootballMatch match;
    private Group group;

    @BeforeEach
    void setUp() {
        sender = new ExpoPushNotificationSender(
                groupRepository,
                groupMemberRepository,
                attendanceRepository,
                capacityService,
                pushDeviceRepository,
                "https://example.invalid/push",
                false);
        match = mock(FootballMatch.class);
        group = mock(Group.class);
        when(group.getName()).thenReturn("Pelada Onze");
    }

    @Test
    void addsEmojiToPaymentDeadlineRemovalTitle() {
        when(match.getStartsAt()).thenReturn(Instant.parse("2026-09-28T20:00:00Z"));
        when(match.getTimeZone()).thenReturn("UTC");

        var copy = sender.copyFor(
                match,
                group,
                MatchNotificationType.PAYMENT_DEADLINE_REMOVAL,
                null);

        assertEquals("Vaga liberada por falta de pagamento ⏰", copy.title());
    }

    @Test
    void addsEmojiToEverySettlementTitlePath() {
        assertEquals(
                "Acerto financeiro atualizado 💳",
                sender.settlementResolvedCopy(match, group, null).title());

        UUID recipientId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchAttendance attendance = mock(MatchAttendance.class);
        when(match.getId()).thenReturn(matchId);
        when(attendanceRepository.findByMatchIdAndUserId(matchId, recipientId))
                .thenReturn(Optional.of(attendance));
        when(attendance.getPaymentSettlementStatus()).thenReturn(
                PaymentSettlementStatus.NOT_RECEIVED,
                PaymentSettlementStatus.RETAINED,
                PaymentSettlementStatus.PENDING);

        assertEquals(
                "Cobrança encerrada ✅",
                sender.settlementResolvedCopy(match, group, recipientId).title());
        assertEquals(
                "Pagamento mantido ✅",
                sender.settlementResolvedCopy(match, group, recipientId).title());
        assertEquals(
                "Acerto financeiro atualizado 💳",
                sender.settlementResolvedCopy(match, group, recipientId).title());
    }
}
