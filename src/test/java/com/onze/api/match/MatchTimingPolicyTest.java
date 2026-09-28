package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatchTimingPolicyTest {
    @Test
    void allowsOneToFourEqualDurationPeriodsAndIndependentOvertimeAndPenalties() {
        var penaltiesOnly = MatchTimingPolicy.resolve(
                true, 1, 20, false, null, null, true, MatchType.INTERNAL, 2);
        assertFalse(penaltiesOnly.overtimeEnabled());
        assertTrue(penaltiesOnly.penaltyShootoutEnabled());

        var overtimeOnly = MatchTimingPolicy.resolve(
                true, 4, 15, true, 4, 5, false, MatchType.VERSUS_EXTERNAL, null);
        assertEquals(4, overtimeOnly.periodCount());
        assertEquals(4, overtimeOnly.overtimePeriodCount());
        assertFalse(overtimeOnly.penaltyShootoutEnabled());

        var both = MatchTimingPolicy.resolve(
                true, 2, 30, true, 2, 10, true, MatchType.INTERNAL, 2);
        assertTrue(both.overtimeEnabled());
        assertTrue(both.penaltyShootoutEnabled());
    }

    @Test
    void rejectsInvalidCountsDurationsAndShootoutsWithMoreThanTwoTeams() {
        assertThrows(MatchTimingPolicy.InvalidMatchTimingConfigurationException.class,
                () -> MatchTimingPolicy.resolve(true, 0, 20, false, null, null, false,
                        MatchType.INTERNAL, 2));
        assertThrows(MatchTimingPolicy.InvalidMatchTimingConfigurationException.class,
                () -> MatchTimingPolicy.resolve(true, 5, 20, false, null, null, false,
                        MatchType.INTERNAL, 2));
        assertThrows(MatchTimingPolicy.InvalidMatchTimingConfigurationException.class,
                () -> MatchTimingPolicy.resolve(true, 2, 0, false, null, null, false,
                        MatchType.INTERNAL, 2));
        assertThrows(MatchTimingPolicy.InvalidMatchTimingConfigurationException.class,
                () -> MatchTimingPolicy.resolve(true, 2, 20, true, 5, 5, false,
                        MatchType.INTERNAL, 2));
        assertThrows(MatchTimingPolicy.InvalidMatchTimingConfigurationException.class,
                () -> MatchTimingPolicy.resolve(true, 2, 20, false, null, null, true,
                        MatchType.INTERNAL, 3));
    }
}
