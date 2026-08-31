package com.onze.api.match;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.onze.api.group.Group;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ExpoPushNotificationSender {

    private static final int EXPO_BATCH_SIZE = 100;
    private static final Locale PORTUGUESE_BRAZIL = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter MATCH_DATE_FORMATTER = DateTimeFormatter.ofPattern(
            "EEE, dd/MM 'às' HH:mm",
            PORTUGUESE_BRAZIL);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final RestClient restClient;
    private final boolean enabled;

    public ExpoPushNotificationSender(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            MatchAttendanceRepository attendanceRepository,
            PushDeviceRepository pushDeviceRepository,
            @Value("${notifications.expo.endpoint}") String endpoint,
            @Value("${notifications.expo.enabled:true}") boolean enabled) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.attendanceRepository = attendanceRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.restClient = RestClient.builder().baseUrl(endpoint).build();
        this.enabled = enabled;
    }

    public void send(
            FootballMatch match,
            MatchNotificationType notificationType,
            UUID recipientUserId) {
        if (!enabled) {
            return;
        }

        Group group = groupRepository.findById(match.getGroupId()).orElse(null);
        if (group == null) {
            return;
        }

        List<UUID> recipientUserIds = recipientUserId == null
                ? groupMemberRepository.findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId())
                        .stream()
                        .map(member -> member.getUserId())
                        .toList()
                : List.of(recipientUserId);
        List<PushDevice> devices = pushDeviceRepository
                .findAllByUserIdInAndActiveTrue(recipientUserIds);
        if (devices.isEmpty()) {
            return;
        }

        NotificationCopy copy = copyFor(match, group, notificationType, recipientUserId);
        for (int start = 0; start < devices.size(); start += EXPO_BATCH_SIZE) {
            List<PushDevice> batch = devices.subList(
                    start,
                    Math.min(start + EXPO_BATCH_SIZE, devices.size()));
            sendBatch(batch, match, notificationType, copy);
        }
    }

    private NotificationCopy copyFor(
            FootballMatch match,
            Group group,
            MatchNotificationType notificationType,
            UUID recipientUserId) {
        String formattedDate = MATCH_DATE_FORMATTER.format(match.getStartsAt().atZone(
                java.time.ZoneId.of(match.getTimeZone())));
        String amount = match.getPaymentAmount() == null
                ? null
                : NumberFormat.getCurrencyInstance(PORTUGUESE_BRAZIL).format(match.getPaymentAmount());

        return switch (notificationType) {
            case MATCH_CREATED -> new NotificationCopy(
                    "Novo jogo marcado ⚽",
                    group.getName() + ": " + formattedDate + ".");
            case ATTENDANCE_OPENED -> new NotificationCopy(
                    "Presença liberada ⚽",
                    "Confirme sua presença no próximo jogo de " + group.getName() + ".");
            case ATTENDANCE_REMINDER -> new NotificationCopy(
                    "Você vai jogar? ⚽",
                    "Você ainda não respondeu sobre o jogo de " + group.getName() + ".");
            case PAYMENT_REMINDER -> new NotificationCopy(
                    "Pagamento pendente 💳",
                    "Sua vaga em " + group.getName() + " está reservada. O pagamento de "
                            + amount + " continua pendente.");
            case PAYMENT_REPORTED -> new NotificationCopy(
                    "Pagamento informado 💳",
                    "Há um pagamento aguardando sua validação no jogo de " + group.getName() + ".");
            case PAYMENT_CONFIRMED -> new NotificationCopy(
                    "Pagamento confirmado ✅",
                    amount == null
                            ? "Seu pagamento para o jogo de " + group.getName() + " foi confirmado."
                            : "Seu pagamento de " + amount + " para " + group.getName() + " foi confirmado.");
            case MATCH_TOMORROW -> tomorrowCopy(match, group, recipientUserId, amount);
            case TEAM_FULL -> new NotificationCopy(
                    "Time fechado ✅",
                    group.getName() + " chegou a "
                            + attendanceRepository.countByMatchIdAndStatus(
                                    match.getId(),
                                    AttendanceStatus.GOING)
                            + "/"
                            + match.getMaxPlayers()
                            + " jogadores confirmados.");
            case MATCH_CANCELLED -> new NotificationCopy(
                    "Jogo cancelado ⚠️",
                    group.getName() + ": a partida de " + formattedDate + " foi cancelada.");
            case SERIES_CANCELLED -> new NotificationCopy(
                    "Jogos semanais encerrados ⚠️",
                    "Os próximos jogos semanais de " + group.getName() + " foram encerrados.");
        };
    }

    private NotificationCopy tomorrowCopy(
            FootballMatch match,
            Group group,
            UUID recipientUserId,
            String amount) {
        PaymentStatus paymentStatus = recipientUserId == null
                ? null
                : attendanceRepository.findByMatchIdAndUserId(match.getId(), recipientUserId)
                        .map(MatchAttendance::getPaymentStatus)
                        .orElse(null);
        if (paymentStatus == PaymentStatus.PENDING) {
            return new NotificationCopy(
                    "Jogo amanhã — pagamento pendente 💳",
                    "Pague " + amount + " e prepare-se para o jogo de " + group.getName() + ".");
        }
        if (paymentStatus == PaymentStatus.REPORTED) {
            return new NotificationCopy(
                    "Jogo amanhã ⚽",
                    "Seu pagamento foi informado e aguarda validação. Prepare-se para "
                            + group.getName() + ".");
        }
        return new NotificationCopy(
                "Jogo amanhã ⚽",
                "Sua presença está confirmada. Prepare-se para o jogo de " + group.getName() + ".");
    }

    @SuppressWarnings("unchecked")
    private void sendBatch(
            List<PushDevice> devices,
            FootballMatch match,
            MatchNotificationType notificationType,
            NotificationCopy copy) {
        List<Map<String, Object>> messages = new ArrayList<>(devices.size());
        for (PushDevice device : devices) {
            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getExpoPushToken());
            message.put("sound", "default");
            message.put("priority", "high");
            message.put("channelId", "matches");
            message.put("title", copy.title());
            message.put("body", copy.body());
            message.put("data", Map.of(
                    "route", "/match",
                    "matchId", match.getId().toString(),
                    "groupId", match.getGroupId().toString(),
                    "notificationType", notificationType.name()));
            messages.add(message);
        }

        Map<String, Object> response = restClient.post()
                .body(messages)
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("data") instanceof List<?> tickets)) {
            throw new IllegalStateException("Expo returned an invalid push ticket response");
        }

        String retryableError = null;
        for (int index = 0; index < tickets.size() && index < devices.size(); index++) {
            Object value = tickets.get(index);
            if (!(value instanceof Map<?, ?> ticket) || !"error".equals(ticket.get("status"))) {
                continue;
            }

            Object detailsValue = ticket.get("details");
            String errorCode = detailsValue instanceof Map<?, ?> details
                    ? String.valueOf(details.get("error"))
                    : "UNKNOWN";
            if ("DeviceNotRegistered".equals(errorCode)) {
                devices.get(index).deactivate();
            } else {
                retryableError = errorCode + ": " + String.valueOf(ticket.get("message"));
            }
        }

        if (retryableError != null) {
            throw new IllegalStateException("Expo push failed: " + retryableError);
        }
    }

    private record NotificationCopy(String title, String body) {
    }
}
