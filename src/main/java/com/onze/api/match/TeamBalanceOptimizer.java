package com.onze.api.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic local optimizer for generated teams.
 *
 * <p>Only swaps assignments that have the same role and the same score source
 * category (real/estimated). This preserves the exact role/goalkeeper composition
 * and the uncertainty distribution selected by the formation algorithm while
 * trying to reduce the difference between team strengths.</p>
 */
final class TeamBalanceOptimizer {

    private static final double EPSILON = 0.000_001;
    private static final int MAX_SWAPS = 64;
    private static final long MAX_PAIR_EVALUATIONS = 250_000L;

    private TeamBalanceOptimizer() {
    }

    static List<Slot> optimize(List<Slot> source, int teamCount) {
        if (teamCount < 2 || source.size() < 2) {
            return List.copyOf(source);
        }

        List<MutableSlot> slots = source.stream()
                .map(MutableSlot::new)
                .toList();
        TeamState state = TeamState.from(slots, teamCount);
        Objective current = state.objective();
        long evaluations = 0;

        for (int swapNumber = 0;
                swapNumber < MAX_SWAPS && evaluations < MAX_PAIR_EVALUATIONS;
                swapNumber++) {
            Swap bestSwap = null;
            Objective bestObjective = current;

            search:
            for (int leftIndex = 0; leftIndex < slots.size(); leftIndex++) {
                MutableSlot left = slots.get(leftIndex);
                for (int rightIndex = leftIndex + 1; rightIndex < slots.size(); rightIndex++) {
                    MutableSlot right = slots.get(rightIndex);
                    if (left.teamNumber == right.teamNumber
                            || !left.role.equals(right.role)
                            || left.estimated != right.estimated) {
                        continue;
                    }
                    if (evaluations >= MAX_PAIR_EVALUATIONS) {
                        break search;
                    }
                    evaluations++;

                    Objective candidate = state.objectiveAfterSwap(left, right);
                    if (candidate.isBetterThan(bestObjective)) {
                        bestObjective = candidate;
                        bestSwap = new Swap(left, right);
                    }
                }
            }

            if (bestSwap == null) {
                break;
            }
            state.apply(bestSwap.left, bestSwap.right);
            current = bestObjective;
        }

        return slots.stream()
                .map(slot -> new Slot(
                        slot.index,
                        slot.teamNumber,
                        slot.role,
                        slot.score,
                        slot.estimated,
                        slot.stableKey))
                .toList();
    }

    record Slot(
            int index,
            int teamNumber,
            String role,
            int score,
            boolean estimated,
            String stableKey) {
        Slot {
            Objects.requireNonNull(role);
            Objects.requireNonNull(stableKey);
        }
    }

    private static final class MutableSlot {
        private final int index;
        private int teamNumber;
        private final String role;
        private final int score;
        private final boolean estimated;
        private final String stableKey;

        MutableSlot(Slot source) {
            index = source.index();
            teamNumber = source.teamNumber();
            role = source.role();
            score = source.score();
            estimated = source.estimated();
            stableKey = source.stableKey();
        }
    }

    private static final class TeamState {
        private final long[] totals;
        private final int[] counts;
        private final int teamCount;

        private TeamState(long[] totals, int[] counts, int teamCount) {
            this.totals = totals;
            this.counts = counts;
            this.teamCount = teamCount;
        }

        static TeamState from(List<MutableSlot> slots, int teamCount) {
            long[] totals = new long[teamCount + 1];
            int[] counts = new int[teamCount + 1];
            for (MutableSlot slot : slots) {
                if (slot.teamNumber < 1 || slot.teamNumber > teamCount) {
                    throw new IllegalArgumentException("Invalid team number");
                }
                totals[slot.teamNumber] += slot.score;
                counts[slot.teamNumber]++;
            }
            return new TeamState(totals, counts, teamCount);
        }

        Objective objective() {
            return objective(0, 0, 0, 0);
        }

        Objective objectiveAfterSwap(MutableSlot left, MutableSlot right) {
            long leftDelta = (long) right.score - left.score;
            long rightDelta = (long) left.score - right.score;
            return objective(left.teamNumber, leftDelta, right.teamNumber, rightDelta);
        }

        void apply(MutableSlot left, MutableSlot right) {
            int leftTeam = left.teamNumber;
            int rightTeam = right.teamNumber;
            totals[leftTeam] += (long) right.score - left.score;
            totals[rightTeam] += (long) left.score - right.score;
            left.teamNumber = rightTeam;
            right.teamNumber = leftTeam;
        }

        private Objective objective(
                int firstTeam,
                long firstDelta,
                int secondTeam,
                long secondDelta) {
            List<Double> averages = new ArrayList<>(teamCount);
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double mean = 0;
            int activeTeams = 0;

            for (int team = 1; team <= teamCount; team++) {
                if (counts[team] == 0) {
                    continue;
                }
                long total = totals[team];
                if (team == firstTeam) {
                    total += firstDelta;
                }
                if (team == secondTeam) {
                    total += secondDelta;
                }
                double average = (double) total / counts[team];
                averages.add(average);
                min = Math.min(min, average);
                max = Math.max(max, average);
                mean += average;
                activeTeams++;
            }

            if (activeTeams == 0) {
                return new Objective(0, 0);
            }
            mean /= activeTeams;
            double dispersion = 0;
            for (double average : averages) {
                double difference = average - mean;
                dispersion += difference * difference;
            }
            return new Objective(max - min, dispersion);
        }
    }

    private record Swap(MutableSlot left, MutableSlot right) {
    }

    private record Objective(double spread, double dispersion) {
        boolean isBetterThan(Objective other) {
            if (spread < other.spread - EPSILON) {
                return true;
            }
            return Math.abs(spread - other.spread) <= EPSILON
                    && dispersion < other.dispersion - EPSILON;
        }
    }
}
