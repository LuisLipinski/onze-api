package com.onze.api.match;

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
    private static final DateTimeFormatter MATCH_DATE_FORMATTER = DateTimeFormatter.ofPattern(
            "EEE, dd/MM 'às' HH:mm",
            Locale.forLanguageTag("pt-BR"));

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final RestClient restClient;
    private final boolean enabled;

    public ExpoPushNotificationSender(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            PushDeviceRepository pushDeviceRepository,
            RestClient.Builder restClientBuilder,
            @Value("${notifications.expo.endpoint}") String endpoint,
            @Value("${notifications.expo.enabled:true}") boolean enabled) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.restClient = restClientBuilder.baseUrl(endpoint).build();
        this.enabled = enabled;
    }

    public void send(FootballMatch match, MatchNotificationType notificationType) {
        if (!enabled) {
            return;
        }

        Group group = groupRepository.findById(match.getGroupId()).orElse(null);
        if (group == null) {
            return;
        }

        List<UUID> memberUserIds = groupMemberRepository
                .findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId())
                .stream()
                .map(member -> member.getUserId())
                .toList();
        List<PushDevice> devices = pushDeviceRepository.findAllByUserIdInAndActiveTrue(memberUserIds);
        if (devices.isEmpty()) {
            return;
        }

        String title = notificationType == MatchNotificationType.ATTENDANCE_OPENED
                ? "Presença liberada ⚽"
                : "Novo jogo marcado ⚽";
        String formattedDate = MATCH_DATE_FORMATTER.format(match.getStartsAt().atZone(
                java.time.ZoneId.of(match.getTimeZone())));
        String body = notificationType == MatchNotificationType.ATTENDANCE_OPENED
                ? "Confirme sua presença no próximo jogo de " + group.getName() + "."
                : group.getName() + ": " + formattedDate + ".";

        for (int start = 0; start < devices.size(); start += EXPO_BATCH_SIZE) {
            List<PushDevice> batch = devices.subList(start, Math.min(start + EXPO_BATCH_SIZE, devices.size()));
            sendBatch(batch, match, title, body);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendBatch(
            List<PushDevice> devices,
            FootballMatch match,
            String title,
            String body) {
        List<Map<String, Object>> messages = new ArrayList<>(devices.size());
        for (PushDevice device : devices) {
            Map<String, Object> message = new HashMap<>();
            message.put("to", device.getExpoPushToken());
            message.put("sound", "default");
            message.put("priority", "high");
            message.put("channelId", "matches");
            message.put("title", title);
            message.put("body", body);
            message.put("data", Map.of(
                    "route", "/match",
                    "matchId", match.getId().toString(),
                    "groupId", match.getGroupId().toString()));
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
}
