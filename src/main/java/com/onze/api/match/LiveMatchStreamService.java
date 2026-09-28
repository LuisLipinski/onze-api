package com.onze.api.match;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.LiveMatchModels.LiveMatchStreamEventResponse;
import com.onze.api.match.LiveMatchModels.LiveMatchSummaryResponse;
import com.onze.api.match.MatchService.MatchNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LiveMatchStreamService {
    private static final long RECONNECT_DELAY_MILLIS = 5_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveMatchStreamService.class);

    private final GroupMemberRepository memberRepository;
    private final FootballMatchRepository matchRepository;
    private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Subscription> feedSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Subscription>> subscriptionsByMatch =
            new ConcurrentHashMap<>();

    public LiveMatchStreamService(
            GroupMemberRepository memberRepository,
            FootballMatchRepository matchRepository) {
        this.memberRepository = memberRepository;
        this.matchRepository = matchRepository;
    }

    public SseEmitter subscribe(String authenticatedUserId, UUID matchId) {
        UUID userId = parseUserId(authenticatedUserId);
        Map<UUID, Boolean> permissionsByGroup = permissionsFor(userId, matchId);

        UUID subscriptionId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(
                subscriptionId, matchId, permissionsByGroup, emitter);
        register(subscription);

        Runnable cleanup = () -> unregister(subscription);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(RECONNECT_DELAY_MILLIS)
                    .data(Map.of("connected", true)));
        } catch (IOException | IllegalStateException exception) {
            fail(subscription, exception);
        }
        return emitter;
    }

    public void broadcast(LiveMatchStreamEventResponse event) {
        feedSubscriptions.values().forEach(subscription -> send(event, subscription, false));
        Map<UUID, Subscription> matchSubscriptions = subscriptionsByMatch.get(event.matchId());
        if (matchSubscriptions != null) {
            matchSubscriptions.values().forEach(subscription -> send(event, subscription, true));
        }
    }

    private void send(
            LiveMatchStreamEventResponse event,
            Subscription subscription,
            boolean includeLiveMatch) {
        Boolean canManage = subscription.permissionsByGroup().get(event.groupId());
        if (canManage == null) return;

        LiveMatchStreamEventResponse personalized = personalize(
                event,
                canManage,
                includeLiveMatch);
        try {
            subscription.emitter().send(SseEmitter.event().data(personalized));
        } catch (IOException | IllegalStateException exception) {
            fail(subscription, exception);
        }
    }

    @Scheduled(fixedDelayString = "${matches.live.heartbeat-delay-ms:25000}")
    public void heartbeat() {
        subscriptions.values().forEach(subscription -> {
            try {
                subscription.emitter().send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException exception) {
                fail(subscription, exception);
            }
        });
    }

    int activeConnectionCount() {
        return subscriptions.size();
    }

    int activeConnectionCount(UUID matchId) {
        Map<UUID, Subscription> matchSubscriptions = subscriptionsByMatch.get(matchId);
        return matchSubscriptions == null ? 0 : matchSubscriptions.size();
    }

    int activeFeedConnectionCount() {
        return feedSubscriptions.size();
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
                summary.phase(),
                summary.currentPeriod(),
                canManage);
        return new LiveMatchStreamEventResponse(
                event.type(),
                event.matchId(),
                event.groupId(),
                event.version(),
                personalizedSummary,
                includeLiveMatch ? event.liveMatch() : null);
    }

    private Map<UUID, Boolean> permissionsFor(UUID userId, UUID matchId) {
        if (matchId == null) {
            return memberRepository
                    .findAllByUserIdOrderByCreatedAtAsc(userId)
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(
                            GroupMember::getGroupId,
                            member -> member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES),
                            (first, ignored) -> first));
        }

        FootballMatch match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);
        GroupMember member = memberRepository
                .findByGroupIdAndUserId(match.getGroupId(), userId)
                .orElseThrow(GroupAccessDeniedException::new);
        return Map.of(
                match.getGroupId(),
                member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
    }

    private void register(Subscription subscription) {
        subscriptions.put(subscription.id(), subscription);
        if (subscription.matchId() == null) {
            feedSubscriptions.put(subscription.id(), subscription);
        } else {
            subscriptionsByMatch.compute(subscription.matchId(), (matchId, current) -> {
                ConcurrentMap<UUID, Subscription> indexed = current == null
                        ? new ConcurrentHashMap<>()
                        : current;
                indexed.put(subscription.id(), subscription);
                return indexed;
            });
        }
        LOGGER.info(
                "Live match SSE connected: matchId={}, activeConnections={}, scopedConnections={}",
                subscription.matchId(),
                activeConnectionCount(),
                scopedConnectionCount(subscription.matchId()));
    }

    private boolean unregister(Subscription subscription) {
        if (!subscriptions.remove(subscription.id(), subscription)) {
            return false;
        }
        if (subscription.matchId() == null) {
            feedSubscriptions.remove(subscription.id(), subscription);
        } else {
            subscriptionsByMatch.computeIfPresent(subscription.matchId(), (matchId, current) -> {
                current.remove(subscription.id(), subscription);
                return current.isEmpty() ? null : current;
            });
        }
        LOGGER.info(
                "Live match SSE disconnected: matchId={}, activeConnections={}, scopedConnections={}",
                subscription.matchId(),
                activeConnectionCount(),
                scopedConnectionCount(subscription.matchId()));
        return true;
    }

    private int scopedConnectionCount(UUID matchId) {
        return matchId == null ? activeFeedConnectionCount() : activeConnectionCount(matchId);
    }

    private void fail(Subscription subscription, Exception exception) {
        if (unregister(subscription)) {
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
