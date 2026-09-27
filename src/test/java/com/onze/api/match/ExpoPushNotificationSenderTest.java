package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
                RestClient.builder(),
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

    @Test
    void logsSuccessfulDeliveryAndRejectedDeviceCleanup(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        sender = new ExpoPushNotificationSender(
                groupRepository,
                groupMemberRepository,
                attendanceRepository,
                capacityService,
                pushDeviceRepository,
                builder,
                "https://example.invalid/push",
                true);
        UUID matchId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        PushDevice acceptedDevice = new PushDevice(recipientId, "ExponentPushToken[accepted]");
        PushDevice rejectedDevice = new PushDevice(recipientId, "ExponentPushToken[rejected]");
        when(match.getId()).thenReturn(matchId);
        when(match.getGroupId()).thenReturn(groupId);
        when(match.getStartsAt()).thenReturn(Instant.parse("2026-09-28T20:00:00Z"));
        when(match.getTimeZone()).thenReturn("UTC");
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(attendanceRepository.findByMatchIdAndUserId(matchId, recipientId))
                .thenReturn(Optional.empty());
        when(pushDeviceRepository.findAllByUserIdInAndActiveTrue(anyCollection()))
                .thenReturn(List.of(acceptedDevice, rejectedDevice));
        server.expect(requestTo("https://example.invalid/push"))
                .andRespond(withSuccess(
                        """
                        {"data":[
                          {"status":"ok","id":"ticket-1"},
                          {"status":"error","message":"Device is not registered",
                           "details":{"error":"DeviceNotRegistered"}}
                        ]}
                        """,
                        MediaType.APPLICATION_JSON));

        sender.send(match, MatchNotificationType.LIVE_MATCH_GOAL, recipientId);

        server.verify();
        assertFalse(rejectedDevice.isActive());
        assertTrue(acceptedDevice.isActive());
        assertTrue(output.getOut().contains("acceptedDevices=1"));
        assertTrue(output.getOut().contains("deactivatedDevices=1"));
        assertTrue(output.getOut().contains("Deactivated rejected Expo push device"));
    }
}
