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
    void shouldFindGlobalTwoTeamImprovementThatRequiresTwoSimultaneousSwaps() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "A", 31),
                slot(1, 1, "B", 31),
                slot(2, 1, "C", 21),
                slot(3, 1, "D", 21),
                slot(4, 2, "A", 25),
                slot(5, 2, "B", 25),
                slot(6, 2, "C", 25),
                slot(7, 2, "D", 25));

        assertThat(spread(initial, 2)).isEqualTo(1.0);

        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(spread(optimized, 2)).isZero();
        assertThat(roundedStrength(optimized, 1))
                .isEqualTo(roundedStrength(optimized, 2));
    }

    @Test
    void shouldPreferAttackAgainstComparableOpponentDefenseWhenOverallTieRemains() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "CENTER_DEFENDER", 10),
                slot(1, 1, "CENTRAL_MIDFIELDER", 15),
                slot(2, 1, "CENTER_FORWARD", 10),
                slot(3, 2, "CENTER_DEFENDER", 15),
                slot(4, 2, "CENTRAL_MIDFIELDER", 10),
                slot(5, 2, "CENTER_FORWARD", 15));

        assertThat(displayedSpread(initial, 2)).isEqualTo(1);
        assertThat(matchupImbalance(initial)).isEqualTo(10);

        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(displayedSpread(optimized, 2)).isEqualTo(1);
        assertThat(matchupImbalance(optimized)).isZero();
        assertThat(roleCountsByTeam(optimized)).isEqualTo(roleCountsByTeam(initial));
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
    void shouldPreserveEstimatedEvaluationDistribution() {
        List<TeamBalanceOptimizer.Slot> initial = List.of(
                slot(0, 1, "A", 45, false),
                slot(1, 1, "B", 35, true),
                slot(2, 2, "A", 15, false),
                slot(3, 2, "B", 25, true));

        Map<Integer, Long> before = estimatedCountsByTeam(initial);
        List<TeamBalanceOptimizer.Slot> optimized = TeamBalanceOptimizer.optimize(initial, 2);

        assertThat(estimatedCountsByTeam(optimized)).isEqualTo(before);
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
        return slot(index, team, role, score, false);
    }

    private TeamBalanceOptimizer.Slot slot(
            int index,
            int team,
            String role,
            int score,
            boolean estimated) {
        return new TeamBalanceOptimizer.Slot(
                index,
                team,
                role,
                score,
                estimated,
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

    private int displayedSpread(List<TeamBalanceOptimizer.Slot> slots, int teamCount) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int team = 1; team <= teamCount; team++) {
            int strength = roundedStrength(slots, team);
            min = Math.min(min, strength);
            max = Math.max(max, strength);
        }
        return max - min;
    }

    private int matchupImbalance(List<TeamBalanceOptimizer.Slot> slots) {
        int firstAttack = roundedRoleStrength(slots, 1, "CENTER_FORWARD");
        int secondAttack = roundedRoleStrength(slots, 2, "CENTER_FORWARD");
        int firstDefense = roundedRoleStrength(slots, 1, "CENTER_DEFENDER");
        int secondDefense = roundedRoleStrength(slots, 2, "CENTER_DEFENDER");
        return Math.abs(
                (firstAttack - secondDefense)
                        - (secondAttack - firstDefense));
    }

    private int roundedRoleStrength(
            List<TeamBalanceOptimizer.Slot> slots,
            int team,
            String role) {
        return (int) Math.round(slots.stream()
                .filter(slot -> slot.teamNumber() == team && role.equals(slot.role()))
                .mapToInt(TeamBalanceOptimizer.Slot::score)
                .average()
                .orElse(0));
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

    private Map<Integer, Long> estimatedCountsByTeam(List<TeamBalanceOptimizer.Slot> slots) {
        return slots.stream()
                .filter(TeamBalanceOptimizer.Slot::estimated)
                .collect(Collectors.groupingBy(
                        TeamBalanceOptimizer.Slot::teamNumber,
                        Collectors.counting()));
    }
}
