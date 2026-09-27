package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MatchNotificationProcessorTest {
    private static final Instant NOW = Instant.parse("2026-09-27T18:00:00Z");

    private MatchNotificationJobRepository jobs;
    private FootballMatchRepository matches;
    private ExpoPushNotificationSender sender;
    private MatchNotificationProcessor processor;
    private MatchNotificationJob job;
    private FootballMatch match;

    @BeforeEach
    void setUp() {
        jobs = mock(MatchNotificationJobRepository.class);
        matches = mock(FootballMatchRepository.class);
        sender = mock(ExpoPushNotificationSender.class);
        processor = new MatchNotificationProcessor(
                jobs,
                matches,
                mock(MatchAttendanceRepository.class),
                mock(MatchCapacityService.class),
                sender,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID matchId = UUID.randomUUID();
        job = new MatchNotificationJob(
                matchId,
                UUID.randomUUID(),
                MatchNotificationType.LIVE_MATCH_GOAL,
                "test-live-goal",
                NOW);
        match = mock(FootballMatch.class);
        when(jobs.findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                any(), any())).thenReturn(List.of(job));
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(match.getStartsAt()).thenReturn(NOW.plusSeconds(3600));
        when(match.getStatus()).thenReturn(MatchStatus.IN_PROGRESS);
    }

    @Test
    void marksSuccessfulNotificationAsSent() {
        assertEquals(1, processor.processPending());
        assertEquals(MatchNotificationStatus.SENT, job.getStatus());
        assertEquals(0, job.getAttempts());
    }

    @Test
    void logsRetryableNotificationFailure(CapturedOutput output) {
        doThrow(new IllegalStateException("Expo unavailable"))
                .when(sender).send(any(), any(), any());

        assertEquals(0, processor.processPending());

        assertEquals(MatchNotificationStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttempts());
        assertTrue(output.getOut().contains("Expo push notification attempt failed"));
        assertTrue(output.getOut().contains("notificationType=LIVE_MATCH_GOAL"));
        assertTrue(output.getOut().contains("reason=Expo unavailable"));
    }

    @Test
    void logsPermanentNotificationFailureAfterFifthAttempt(CapturedOutput output) {
        doThrow(new IllegalStateException())
                .when(sender).send(any(), any(), any());

        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(0, processor.processPending());
        }

        assertEquals(MatchNotificationStatus.FAILED, job.getStatus());
        assertEquals(5, job.getAttempts());
        assertTrue(output.getOut().contains("Expo push notification permanently failed"));
        assertTrue(output.getOut().contains("attempts=5"));
        assertTrue(output.getOut().contains("reason=IllegalStateException"));
    }
}
