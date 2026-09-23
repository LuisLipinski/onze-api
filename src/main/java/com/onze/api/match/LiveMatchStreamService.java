package com.onze.api.match;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.LiveMatchModels.LiveMatchStreamEventResponse;
import com.onze.api.match.LiveMatchModels.LiveMatchSummaryResponse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LiveMatchStreamService {
    private static final long RECONNECT_DELAY_MILLIS = 5_000L;

    private final GroupMemberRepository memberRepository;
    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();

    public LiveMatchStreamService(GroupMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public SseEmitter subscribe(String authenticatedUserId, UUID matchId) {
        UUID userId = parseUserId(authenticatedUserId);
        Map<UUID, Boolean> permissionsByGroup = memberRepository
                .findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        GroupMember::getGroupId,
                        member -> member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES),
                        (first, ignored) -> first));

        UUID subscriptionId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(
                subscriptionId, matchId, permissionsByGroup, emitter);
        subscriptions.put(subscriptionId, subscription);

        Runnable cleanup = () -> subscriptions.remove(subscriptionId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(RECONNECT_DELAY_MILLIS)
                    .data(Map.of("connected", true)));
        } catch (IOException exception) {
            remove(subscription, exception);
        }
        return emitter;
    }

    public void broadcast(LiveMatchStreamEventResponse event) {
        subscriptions.values().forEach(subscription -> {
            Boolean canManage = subscription.permissionsByGroup().get(event.groupId());
            if (canManage == null) return;
            if (subscription.matchId() != null && !subscription.matchId().equals(event.matchId())) return;

            LiveMatchStreamEventResponse personalized = personalize(
                    event,
                    canManage,
                    subscription.matchId() != null);
            try {
                subscription.emitter().send(SseEmitter.event().data(personalized));
            } catch (IOException | IllegalStateException exception) {
                remove(subscription, exception);
            }
        });
    }

    @Scheduled(fixedDelayString = "${matches.live.heartbeat-delay-ms:25000}")
    public void heartbeat() {
        subscriptions.values().forEach(subscription -> {
            try {
                subscription.emitter().send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException exception) {
                remove(subscription, exception);
            }
        });
    }

    int activeConnectionCount() {
        return subscriptions.size();
    }

    private LiveMatchStreamEventResponse personalize(
            LiveMatchStreamEventResponse event,
            boolean canManage,
            boolean includeLiveMatch) {
        LiveMatchSummaryResponse summary = event.summary();
        LiveMatchSummaryResponse personalizedSummary = summary == null ? null : new LiveMatchSummaryResponse(
                summary.matchId(),
                summary.groupId(),
                summary.groupName(),
                summary.startsAt(),
                summary.timeZone(),
                summary.venue(),
                summary.status(),
                summary.startedAt(),
                summary.matchType(),
                summary.teamCount(),
                summary.version(),
                summary.scores(),
                canManage);
        return new LiveMatchStreamEventResponse(
                event.type(),
                event.matchId(),
                event.groupId(),
                event.version(),
                personalizedSummary,
                includeLiveMatch ? event.liveMatch() : null);
    }

    private void remove(Subscription subscription, Exception exception) {
        if (subscriptions.remove(subscription.id(), subscription)) {
            subscription.emitter().completeWithError(exception);
        }
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
    }

    private record Subscription(
            UUID id,
            UUID matchId,
            Map<UUID, Boolean> permissionsByGroup,
            SseEmitter emitter) {
    }
}
