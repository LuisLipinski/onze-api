package com.onze.api.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic optimizer for generated teams.
 *
 * <p>For two normal-sized teams, explores every reachable distribution while
 * preserving each team's quantity per role and per score-source category
 * (real/estimated). For larger or multi-team scenarios, uses a bounded local
 * search. This keeps the API cost safe while avoiding local minima in the most
 * common two-team case.</p>
 */
final class TeamBalanceOptimizer {

    private static final double EPSILON = 0.000_001;
    private static final int MAX_EXACT_SLOTS = 30;
    private static final int MAX_BUCKET_OPTIONS = 20_000;
    private static final long MAX_EXACT_CONFIGURATIONS = 200_000L;
    private static final int MAX_SWAPS = 64;
    private static final long MAX_PAIR_EVALUATIONS = 250_000L;

    private TeamBalanceOptimizer() {
    }

    static List<Slot> optimize(List<Slot> source, int teamCount) {
        if (teamCount < 2 || source.size() < 2) {
            return List.copyOf(source);
        }

        if (teamCount == 2 && source.size() <= MAX_EXACT_SLOTS) {
            List<Slot> exact = exactTwoTeams(source);
            if (exact != null) {
                return exact;
            }
        }
        return localOptimize(source, teamCount);
    }

    private static List<Slot> exactTwoTeams(List<Slot> source) {
        List<MutableSlot> slots = mutableSlots(source);
        int teamOneCount = (int) slots.stream().filter(slot -> slot.teamNumber == 1).count();
        int teamTwoCount = (int) slots.stream().filter(slot -> slot.teamNumber == 2).count();
        if (teamOneCount == 0 || teamTwoCount == 0
                || teamOneCount + teamTwoCount != slots.size()) {
            return null;
        }

        Map<String, List<MutableSlot>> grouped = new TreeMap<>();
        for (MutableSlot slot : slots) {
            grouped.computeIfAbsent(
                    slot.role + "\u0000" + slot.estimated,
                    ignored -> new ArrayList<>())
                    .add(slot);
        }

        List<Bucket> buckets = new ArrayList<>();
        for (List<MutableSlot> bucketSlots : grouped.values()) {
            bucketSlots.sort(Comparator
                    .comparing((MutableSlot slot) -> slot.stableKey)
                    .thenComparingInt(slot -> slot.index));
            int requiredTeamOne = (int) bucketSlots.stream()
                    .filter(slot -> slot.teamNumber == 1)
                    .count();
            List<BucketOption> options = new ArrayList<>();
            collectBucketOptions(
                    bucketSlots,
                    requiredTeamOne,
                    0,
                    0,
                    new boolean[bucketSlots.size()],
                    options);
            if (options.isEmpty() || options.size() > MAX_BUCKET_OPTIONS) {
                return null;
            }
            buckets.add(new Bucket(List.copyOf(bucketSlots), List.copyOf(options)));
        }

        long totalScore = slots.stream().mapToLong(slot -> slot.score).sum();
        ExactSearch search = new ExactSearch(
                buckets, teamOneCount, teamTwoCount, totalScore);
        search.visit(0, 0, new int[buckets.size()]);
        if (search.aborted || search.bestChoices == null) {
            return null;
        }

        for (int bucketIndex = 0; bucketIndex < buckets.size(); bucketIndex++) {
            Bucket bucket = buckets.get(bucketIndex);
            BucketOption option = bucket.options().get(search.bestChoices[bucketIndex]);
            for (int slotIndex = 0; slotIndex < bucket.slots().size(); slotIndex++) {
                bucket.slots().get(slotIndex).teamNumber = option.teamOne()[slotIndex] ? 1 : 2;
            }
        }
        return immutableSlots(slots);
    }

    private static void collectBucketOptions(
            List<MutableSlot> slots,
            int required,
            int index,
            int selected,
            boolean[] teamOne,
            List<BucketOption> options) {
        if (options.size() > MAX_BUCKET_OPTIONS) {
            return;
        }
        if (selected > required || selected + (slots.size() - index) < required) {
            return;
        }
        if (index == slots.size()) {
            if (selected == required) {
                long score = 0;
                for (int current = 0; current < slots.size(); current++) {
                    if (teamOne[current]) {
                        score += slots.get(current).score;
                    }
                }
                options.add(new BucketOption(teamOne.clone(), score));
            }
            return;
        }

        teamOne[index] = true;
        collectBucketOptions(slots, required, index + 1, selected + 1, teamOne, options);
        teamOne[index] = false;
        collectBucketOptions(slots, required, index + 1, selected, teamOne, options);
    }

    private static List<Slot> localOptimize(List<Slot> source, int teamCount) {
        List<MutableSlot> slots = mutableSlots(source);
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
        return immutableSlots(slots);
    }

    private static List<MutableSlot> mutableSlots(List<Slot> source) {
        List<MutableSlot> slots = new ArrayList<>(source.size());
        for (Slot slot : source) {
            slots.add(new MutableSlot(slot));
        }
        return slots;
    }

    private static List<Slot> immutableSlots(List<MutableSlot> slots) {
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

        Slot(
                int index,
                int teamNumber,
                String role,
                int score,
                String stableKey) {
            this(index, teamNumber, role, score, false, stableKey);
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

    private record Bucket(List<MutableSlot> slots, List<BucketOption> options) {
    }

    private record BucketOption(boolean[] teamOne, long teamOneScore) {
    }

    private static final class ExactSearch {
        private final List<Bucket> buckets;
        private final int teamOneCount;
        private final int teamTwoCount;
        private final long totalScore;
        private long visited;
        private boolean aborted;
        private Objective bestObjective;
        private int[] bestChoices;

        ExactSearch(
                List<Bucket> buckets,
                int teamOneCount,
                int teamTwoCount,
                long totalScore) {
            this.buckets = buckets;
            this.teamOneCount = teamOneCount;
            this.teamTwoCount = teamTwoCount;
            this.totalScore = totalScore;
        }

        void visit(int bucketIndex, long teamOneScore, int[] choices) {
            if (aborted) {
                return;
            }
            if (bucketIndex == buckets.size()) {
                visited++;
                if (visited > MAX_EXACT_CONFIGURATIONS) {
                    aborted = true;
                    return;
                }
                double firstAverage = (double) teamOneScore / teamOneCount;
                double secondAverage = (double) (totalScore - teamOneScore) / teamTwoCount;
                Objective candidate = Objective.of(List.of(firstAverage, secondAverage));
                if (bestObjective == null || candidate.isBetterThan(bestObjective)) {
                    bestObjective = candidate;
                    bestChoices = choices.clone();
                }
                return;
            }

            List<BucketOption> options = buckets.get(bucketIndex).options();
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                choices[bucketIndex] = optionIndex;
                visit(
                        bucketIndex + 1,
                        teamOneScore + options.get(optionIndex).teamOneScore(),
                        choices);
                if (aborted) {
                    return;
                }
            }
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
                averages.add((double) total / counts[team]);
            }
            return Objective.of(averages);
        }
    }

    private record Swap(MutableSlot left, MutableSlot right) {
    }

    private record Objective(int displayedSpread, double rawSpread, double dispersion) {
        static Objective of(List<Double> averages) {
            if (averages.isEmpty()) {
                return new Objective(0, 0, 0);
            }
            int minDisplayed = Integer.MAX_VALUE;
            int maxDisplayed = Integer.MIN_VALUE;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double mean = 0;
            for (double average : averages) {
                int displayed = (int) Math.round(average);
                minDisplayed = Math.min(minDisplayed, displayed);
                maxDisplayed = Math.max(maxDisplayed, displayed);
                min = Math.min(min, average);
                max = Math.max(max, average);
                mean += average;
            }
            mean /= averages.size();
            double dispersion = 0;
            for (double average : averages) {
                double difference = average - mean;
                dispersion += difference * difference;
            }
            return new Objective(maxDisplayed - minDisplayed, max - min, dispersion);
        }

        boolean isBetterThan(Objective other) {
            if (displayedSpread != other.displayedSpread) {
                return displayedSpread < other.displayedSpread;
            }
            if (rawSpread < other.rawSpread - EPSILON) {
                return true;
            }
            return Math.abs(rawSpread - other.rawSpread) <= EPSILON
                    && dispersion < other.dispersion - EPSILON;
        }
    }
}
