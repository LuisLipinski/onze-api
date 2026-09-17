package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TeamDiversityOptimizerTest {

    @Test
    void normalizedDivisionIgnoresTeamNumberSwap() {
        List<TeamBalanceOptimizer.Slot> original = slots("A", "B", "C", "D");
        List<TeamBalanceOptimizer.Slot> swappedLabels = original.stream()
                .map(slot -> new TeamBalanceOptimizer.Slot(
                        slot.index(),
                        slot.teamNumber() == 1 ? 2 : 1,
                        slot.role(),
                        slot.score(),
                        slot.estimated(),
                        slot.stableKey()))
                .toList();

        assertEquals(
                TeamDiversityOptimizer.normalizedDivision(original, 2),
                TeamDiversityOptimizer.normalizedDivision(swappedLabels, 2));
    }

    @Test
    void noHistoryKeepsTechnicalBest() {
        List<TeamBalanceOptimizer.Slot> baseline = slots("A", "B", "C", "D");

        TeamDiversityOptimizer.Result result = TeamDiversityOptimizer.diversify(
                baseline,
                2,
                new TeamDiversityOptimizer.Context(Set.of(), Map.of(), 1));

        assertEquals(
                TeamDiversityOptimizer.normalizedDivision(baseline, 2),
                TeamDiversityOptimizer.normalizedDivision(result.slots(), 2));
        assertFalse(result.forcedRepeat());
    }

    @Test
    void blockedRecentDivisionChoosesAnotherTechnicallyEquivalentSplit() {
        List<TeamBalanceOptimizer.Slot> baseline = slots("A", "B", "C", "D");
        String previous = TeamDiversityOptimizer.normalizedDivision(baseline, 2);

        TeamDiversityOptimizer.Result result = TeamDiversityOptimizer.diversify(
                baseline,
                2,
                new TeamDiversityOptimizer.Context(Set.of(previous), Map.of(), 2));

        assertNotEquals(previous, TeamDiversityOptimizer.normalizedDivision(result.slots(), 2));
        assertFalse(result.forcedRepeat());
    }

    @Test
    void repeatedTeammatesArePenalizedWithMoreRecentHistory() {
        List<TeamBalanceOptimizer.Slot> baseline = slots("A", "B", "C", "D");
        Map<String, Double> weights = new HashMap<>();
        TeamDiversityOptimizer.accumulateTeammateWeights(weights, baseline, 5.0);

        TeamDiversityOptimizer.Result result = TeamDiversityOptimizer.diversify(
                baseline,
                2,
                new TeamDiversityOptimizer.Context(Set.of(), weights, 3));

        assertFalse(areTeammates(result.slots(), "A", "B"));
        assertFalse(areTeammates(result.slots(), "C", "D"));
    }

    @Test
    void consecutiveRegenerationsCycleThroughAvailableEquivalentDivisions() {
        List<TeamBalanceOptimizer.Slot> baseline = slots("A", "B", "C", "D");
        Set<String> blocked = new HashSet<>();
        blocked.add(TeamDiversityOptimizer.normalizedDivision(baseline, 2));

        TeamDiversityOptimizer.Result first = TeamDiversityOptimizer.diversify(
                baseline,
                2,
                new TeamDiversityOptimizer.Context(blocked, Map.of(), 4));
        String firstSignature = TeamDiversityOptimizer.normalizedDivision(first.slots(), 2);
        blocked.add(firstSignature);

        TeamDiversityOptimizer.Result second = TeamDiversityOptimizer.diversify(
                baseline,
                2,
                new TeamDiversityOptimizer.Context(blocked, Map.of(), 5));
        String secondSignature = TeamDiversityOptimizer.normalizedDivision(second.slots(), 2);

        assertFalse(first.forcedRepeat());
        assertFalse(second.forcedRepeat());
        assertNotEquals(firstSignature, secondSignature);
        assertFalse(blocked.contains(secondSignature));
    }

    @Test
    void changedRosterStillUsesTeammateHistoryToVaryPairs() {
        List<TeamBalanceOptimizer.Slot> oldMatch = slots("A", "B", "C", "D");
        Map<String, Double> weights = new HashMap<>();
        TeamDiversityOptimizer.accumulateTeammateWeights(weights, oldMatch, 5.0);

        List<TeamBalanceOptimizer.Slot> current = slots("A", "B", "C", "E");
        TeamDiversityOptimizer.Result result = TeamDiversityOptimizer.diversify(
                current,
                2,
                new TeamDiversityOptimizer.Context(Set.of(), weights, 6));

        assertFalse(areTeammates(result.slots(), "A", "B"));
    }

    @Test
    void forcedRepeatIsReportedWhenNoDifferentNormalizedDivisionExists() {
        List<TeamBalanceOptimizer.Slot> baseline = List.of(
                slot(0, 1, "A", "GOALKEEPER", 25),
                slot(1, 2, "B", "GOALKEEPER", 25));
        String onlyDivision = TeamDiversityOptimizer.normalizedDivision(baseline, 2);

        TeamDiversityOptimizer.Result result = TeamDiversityOptimizer.diversify(
                baseline,
                2,
                new TeamDiversityOptimizer.Context(Set.of(onlyDivision), Map.of(), 7));

        assertEquals(onlyDivision, TeamDiversityOptimizer.normalizedDivision(result.slots(), 2));
        assertTrue(result.forcedRepeat());
    }

    private static List<TeamBalanceOptimizer.Slot> slots(String... keys) {
        List<TeamBalanceOptimizer.Slot> result = new ArrayList<>();
        for (int index = 0; index < keys.length; index++) {
            result.add(slot(index, index < keys.length / 2 ? 1 : 2, keys[index], "MIDFIELDER", 25));
        }
        return result;
    }

    private static TeamBalanceOptimizer.Slot slot(
            int index,
            int team,
            String key,
            String role,
            int score) {
        return new TeamBalanceOptimizer.Slot(index, team, role, score, false, key);
    }

    private static boolean areTeammates(
            List<TeamBalanceOptimizer.Slot> slots,
            String left,
            String right) {
        int leftTeam = slots.stream()
                .filter(slot -> slot.stableKey().equals(left))
                .findFirst()
                .orElseThrow()
                .teamNumber();
        int rightTeam = slots.stream()
                .filter(slot -> slot.stableKey().equals(right))
                .findFirst()
                .orElseThrow()
                .teamNumber();
        return leftTeam == rightTeam;
    }
}
