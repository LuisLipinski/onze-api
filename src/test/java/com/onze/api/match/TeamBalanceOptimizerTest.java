package com.onze.api.match;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamBalanceOptimizerTest {

    @Test
    void shouldImproveTwoTeamsWhenAValidSameRoleSwapExists() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "A", 45),
                slot(1, 1, "B", 35),
                slot(2, 2, "A", 15),
                slot(3, 2, "B", 25));

        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(spread(optimized, 2)).isLessThan(spread(initial, 2));
        assertThat(spread(optimized, 2)).isEqualTo(10.0);
    }

    @Test
    void shouldKeepTheSmallestReachableDifferenceWhenZeroIsImpossible() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "A", 50),
                slot(1, 1, "B", 30),
                slot(2, 2, "A", 20),
                slot(3, 2, "B", 10));

        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(spread(optimized, 2)).isEqualTo(5.0);
    }

    @Test
    void shouldReduceSpreadAcrossThreeTeams() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "A", 50),
                slot(1, 1, "B", 40),
                slot(2, 2, "A", 30),
                slot(3, 2, "B", 30),
                slot(4, 3, "A", 10),
                slot(5, 3, "B", 20));

        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 3);

        assertThat(spread(optimized, 3)).isLessThan(spread(initial, 3));
    }

    @Test
    void shouldPreserveRoleCompositionIncludingGoalkeepers() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "GOALKEEPER", 40),
                slot(1, 1, "CENTER_DEFENDER", 38),
                slot(2, 1, "CENTER_FORWARD", 36),
                slot(3, 2, "GOALKEEPER", 20),
                slot(4, 2, "CENTER_DEFENDER", 18),
                slot(5, 2, "CENTER_FORWARD", 16));

        Map<String, Long> before = roleCountsByTeam(initial);
        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(roleCountsByTeam(optimized)).isEqualTo(before);
    }

    @Test
    void shouldImproveAFormationComparableToTwentyNineVersusTwenty() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "A", 40),
                slot(1, 1, "B", 30),
                slot(2, 1, "C", 17),
                slot(3, 2, "A", 20),
                slot(4, 2, "B", 20),
                slot(5, 2, "C", 20));

        assertThat(roundedStrength(initial, 1)).isEqualTo(29);
        assertThat(roundedStrength(initial, 2)).isEqualTo(20);

        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(spread(optimized, 2)).isLessThan(9.0);
    }

    private TeamBalanceOptimizer.Slot slot(
            int index,
            int team,
            String role,
            int score) {
        return new TeamBalanceOptimizer.Slot(
                index,
                team,
                role,
                score,
                role + "-" + index);
    }

    private double spread(List<TeamBalanceOptimizer.Slot> slots, int teamCount) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int team = 1; team <= teamCount; team++) {
            double average = average(slots, team);
            min = Math.min(min, average);
            max = Math.max(max, average);
        }
        return max - min;
    }

    private int roundedStrength(List<TeamBalanceOptimizer.Slot> slots, int team) {
        return (int) Math.round(average(slots, team));
    }

    private double average(List<TeamBalanceOptimizer.Slot> slots, int team) {
        return slots.stream()
                .filter(slot -> slot.teamNumber() == team)
                .mapToInt(TeamBalanceOptimizer.Slot::score)
                .average()
                .orElse(0);
    }

    private Map<String, Long> roleCountsByTeam(List<TeamBalanceOptimizer.Slot> slots) {
        return slots.stream().collect(Collectors.groupingBy(
                slot -> slot.teamNumber() + ":" + slot.role(),
                Collectors.counting()));
    }
}
