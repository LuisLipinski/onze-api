package com.onze.api.match;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.match.LiveMatchModels.LiveMatchSnapshotResponse;
import com.onze.api.match.LiveMatchModels.LiveMatchSummaryResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveMatchChangedListenerTest {
    private LiveMatchService liveMatchService;
    private LiveMatchFeedService feedService;
    private LiveMatchStreamService streamService;
    private MatchNotificationProcessor notificationProcessor;
    private LiveMatchChangedListener listener;
    private UUID matchId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        liveMatchService = mock(LiveMatchService.class);
        feedService = mock(LiveMatchFeedService.class);
        streamService = mock(LiveMatchStreamService.class);
        notificationProcessor = mock(MatchNotificationProcessor.class);
        listener = new LiveMatchChangedListener(
                liveMatchService, feedService, streamService, notificationProcessor);
        matchId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        var snapshot = new LiveMatchSnapshotResponse(
                matchId,
                MatchStatus.IN_PROGRESS,
                Instant.parse("2026-09-23T18:00:00Z"),
                null,
                4L,
                List.of(),
                List.of(),
                List.of());
        var summary = new LiveMatchSummaryResponse(
                matchId,
                groupId,
                "Onze FC",
                Instant.parse("2026-09-23T18:00:00Z"),
                "America/Sao_Paulo",
                "Arena",
                MatchStatus.IN_PROGRESS,
                Instant.parse("2026-09-23T18:00:00Z"),
                MatchType.INTERNAL,
                2,
                4L,
                List.of(),
                false);
        when(liveMatchService.getSnapshot(matchId)).thenReturn(Optional.of(snapshot));
        when(feedService.getSummary(matchId)).thenReturn(Optional.of(summary));
    }

    @Test
    void broadcastsCommittedStateAndProcessesGoalPushImmediately() {
        listener.onLiveMatchChanged(new LiveMatchChangedEvent(
                matchId, groupId, 4L, LiveMatchChangeType.GOAL_ADDED));

        verify(streamService).broadcast(argThat(event ->
                event.matchId().equals(matchId)
                        && event.groupId().equals(groupId)
                        && event.type() == LiveMatchChangeType.GOAL_ADDED
                        && event.liveMatch().version() == 4L));
        verify(notificationProcessor).processPendingLiveMatch();
    }

    @Test
    void broadcastsScoreChangeWithoutRunningPushQueue() {
        listener.onLiveMatchChanged(new LiveMatchChangedEvent(
                matchId, groupId, 4L, LiveMatchChangeType.SCORE_UPDATED));

        verify(streamService).broadcast(argThat(event ->
                event.type() == LiveMatchChangeType.SCORE_UPDATED));
        verify(notificationProcessor, never()).processPendingLiveMatch();
    }
}
