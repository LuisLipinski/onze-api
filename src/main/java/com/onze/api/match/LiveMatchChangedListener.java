package com.onze.api.match;

import com.onze.api.match.LiveMatchModels.LiveMatchStreamEventResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LiveMatchChangedListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveMatchChangedListener.class);

    private final LiveMatchService liveMatchService;
    private final LiveMatchFeedService liveMatchFeedService;
    private final LiveMatchStreamService streamService;
    private final MatchNotificationProcessor notificationProcessor;

    public LiveMatchChangedListener(
            LiveMatchService liveMatchService,
            LiveMatchFeedService liveMatchFeedService,
            LiveMatchStreamService streamService,
            MatchNotificationProcessor notificationProcessor) {
        this.liveMatchService = liveMatchService;
        this.liveMatchFeedService = liveMatchFeedService;
        this.streamService = streamService;
        this.notificationProcessor = notificationProcessor;
    }

    @Async(LiveMatchAsyncConfiguration.EXECUTOR_NAME)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiveMatchChanged(LiveMatchChangedEvent event) {
        try {
            var snapshot = liveMatchService.getSnapshot(event.matchId());
            var summary = liveMatchFeedService.getSummary(event.matchId());
            if (snapshot.isPresent() && summary.isPresent()) {
                streamService.broadcast(new LiveMatchStreamEventResponse(
                        event.type(),
                        event.matchId(),
                        event.groupId(),
                        snapshot.get().version(),
                        summary.get(),
                        snapshot.get()));
            }
            if (hasImmediatePush(event.type())) {
                notificationProcessor.processPendingLiveMatch();
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Could not publish live match update for {}", event.matchId(), exception);
        }
    }

    private boolean hasImmediatePush(LiveMatchChangeType type) {
        return type == LiveMatchChangeType.MATCH_STARTED
                || type == LiveMatchChangeType.GOAL_ADDED
                || type == LiveMatchChangeType.CARD_ADDED
                || type == LiveMatchChangeType.MATCH_FINISHED;
    }
}
