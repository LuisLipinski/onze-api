package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PenaltyShootoutRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-28T18:00:00Z");

    @Test
    void endsEarlyOnlyWhenTheOtherTeamCannotCatchUp() {
        List<MatchPenaltyAttempt> attempts = new ArrayList<>();
        for (int sequence = 1; sequence <= 5; sequence++) {
            attempts.add(attempt(sequence, sequence % 2 == 1));
        }
        assertNull(LiveMatchService.penaltyWinner(attempts));

        attempts.add(attempt(6, false));
        assertEquals(1, LiveMatchService.penaltyWinner(attempts));
    }

    @Test
    void suddenDeathWaitsForBothTeamsToTakeTheirKick() {
        List<MatchPenaltyAttempt> attempts = new ArrayList<>();
        for (int sequence = 1; sequence <= 10; sequence++) {
            attempts.add(attempt(sequence, true));
        }
        assertNull(LiveMatchService.penaltyWinner(attempts));
        attempts.add(attempt(11, true));
        assertNull(LiveMatchService.penaltyWinner(attempts));
        attempts.add(attempt(12, false));
        assertEquals(1, LiveMatchService.penaltyWinner(attempts));
    }

    private MatchPenaltyAttempt attempt(int sequence, boolean scored) {
        return new MatchPenaltyAttempt(UUID.randomUUID(), sequence, (sequence + 1) / 2,
                sequence % 2 == 1 ? 1 : 2, null, "Batedor", scored, UUID.randomUUID(), NOW);
    }
}
