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
 * (real/estimated). The objective first keeps the displayed overall strengths
 * close and then balances how attack faces the opponent defense, midfield and
 * the individual team lines. For larger or multi-team scenarios, it uses a
 * bounded local search with the same objective where applicable.</p>
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
            buckets.add(new Bucket(
                    List.copyOf(bucketSlots),
                    List.copyOf(options),
                    bucketSlots.get(0).line));
        }

        long totalScore = slots.stream().mapToLong(slot -> slot.score).sum();
        long[] totalLineScores = new long[TeamLine.values().length];
        int[] teamOneLineCounts = new int[TeamLine.values().length];
        int[] teamTwoLineCounts = new int[TeamLine.values().length];
        for (MutableSlot slot : slots) {
            int line = slot.line.ordinal();
            totalLineScores[line] += slot.score;
            if (slot.teamNumber == 1) {
                teamOneLineCounts[line]++;
            } else {
                teamTwoLineCounts[line]++;
            }
        }

        ExactSearch search = new ExactSearch(
                buckets,
                teamOneCount,
                teamTwoCount,
                totalScore,
                totalLineScores,
                teamOneLineCounts,
                teamTwoLineCounts);
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

    private enum TeamLine {
        DEFENSE,
        MIDFIELD,
        ATTACK,
        OTHER
    }

    private static TeamLine lineForRole(String role) {
        return switch (role) {
            case "DEFENDER", "RIGHT_DEFENDER", "LEFT_DEFENDER", "CENTER_DEFENDER",
                    "RIGHT_BACK", "LEFT_BACK", "FIXO" -> TeamLine.DEFENSE;
            case "DEFENSIVE_MIDFIELDER", "MIDFIELDER", "RIGHT_MIDFIELDER",
                    "LEFT_MIDFIELDER", "CENTRAL_MIDFIELDER", "PLAYMAKER",
                    "RIGHT_WINGER_FUTSAL", "LEFT_WINGER_FUTSAL" -> TeamLine.MIDFIELD;
            case "ATTACKER", "RIGHT_WINGER", "LEFT_WINGER", "CENTER_FORWARD",
                    "PIVOT" -> TeamLine.ATTACK;
            default -> TeamLine.OTHER;
        };
    }

    private static final class MutableSlot {
        private final int index;
        private int teamNumber;
        private final String role;
        private final TeamLine line;
        private final int score;
        private final boolean estimated;
        private final String stableKey;

        MutableSlot(Slot source) {
            index = source.index();
            teamNumber = source.teamNumber();
            role = source.role();
            line = lineForRole(source.role());
            score = source.score();
            estimated = source.estimated();
            stableKey = source.stableKey();
        }
    }

    private record Bucket(
            List<MutableSlot> slots,
            List<BucketOption> options,
            TeamLine line) {
    }

    private record BucketOption(boolean[] teamOne, long teamOneScore) {
    }

    private static final class ExactSearch {
        private final List<Bucket> buckets;
        private final int teamOneCount;
        private final int teamTwoCount;
        private final long totalScore;
        private final long[] totalLineScores;
        private final int[] teamOneLineCounts;
        private final int[] teamTwoLineCounts;
        private long visited;
        private boolean aborted;
        private Objective bestObjective;
        private int[] bestChoices;

        ExactSearch(
                List<Bucket> buckets,
                int teamOneCount,
                int teamTwoCount,
                long totalScore,
                long[] totalLineScores,
                int[] teamOneLineCounts,
                int[] teamTwoLineCounts) {
            this.buckets = buckets;
            this.teamOneCount = teamOneCount;
            this.teamTwoCount = teamTwoCount;
            this.totalScore = totalScore;
            this.totalLineScores = totalLineScores;
            this.teamOneLineCounts = teamOneLineCounts;
            this.teamTwoLineCounts = teamTwoLineCounts;
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

                long[] teamOneLineScores = new long[TeamLine.values().length];
                for (int index = 0; index < buckets.size(); index++) {
                    Bucket bucket = buckets.get(index);
                    BucketOption option = bucket.options().get(choices[index]);
                    teamOneLineScores[bucket.line().ordinal()] += option.teamOneScore();
                }
                Objective candidate = Objective.forTwoTeams(
                        teamOneScore,
                        teamOneCount,
                        totalScore - teamOneScore,
                        teamTwoCount,
                        teamOneLineScores,
                        totalLineScores,
                        teamOneLineCounts,
                        teamTwoLineCounts);
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
        private final long[][] lineTotals;
        private final int[][] lineCounts;
        private final int teamCount;

        private TeamState(
                long[] totals,
                int[] counts,
                long[][] lineTotals,
                int[][] lineCounts,
                int teamCount) {
            this.totals = totals;
            this.counts = counts;
            this.lineTotals = lineTotals;
            this.lineCounts = lineCounts;
            this.teamCount = teamCount;
        }

        static TeamState from(List<MutableSlot> slots, int teamCount) {
            long[] totals = new long[teamCount + 1];
            int[] counts = new int[teamCount + 1];
            long[][] lineTotals = new long[teamCount + 1][TeamLine.values().length];
            int[][] lineCounts = new int[teamCount + 1][TeamLine.values().length];
            for (MutableSlot slot : slots) {
                if (slot.teamNumber < 1 || slot.teamNumber > teamCount) {
                    throw new IllegalArgumentException("Invalid team number");
                }
                totals[slot.teamNumber] += slot.score;
                counts[slot.teamNumber]++;
                lineTotals[slot.teamNumber][slot.line.ordinal()] += slot.score;
                lineCounts[slot.teamNumber][slot.line.ordinal()]++;
            }
            return new TeamState(totals, counts, lineTotals, lineCounts, teamCount);
        }

        Objective objective() {
            return objective(0, 0, 0, 0, TeamLine.OTHER);
        }

        Objective objectiveAfterSwap(MutableSlot left, MutableSlot right) {
            long leftDelta = (long) right.score - left.score;
            long rightDelta = (long) left.score - right.score;
            return objective(
                    left.teamNumber,
                    leftDelta,
                    right.teamNumber,
                    rightDelta,
                    left.line);
        }

        void apply(MutableSlot left, MutableSlot right) {
            int leftTeam = left.teamNumber;
            int rightTeam = right.teamNumber;
            long leftDelta = (long) right.score - left.score;
            long rightDelta = (long) left.score - right.score;
            totals[leftTeam] += leftDelta;
            totals[rightTeam] += rightDelta;
            lineTotals[leftTeam][left.line.ordinal()] += leftDelta;
            lineTotals[rightTeam][left.line.ordinal()] += rightDelta;
            left.teamNumber = rightTeam;
            right.teamNumber = leftTeam;
        }

        private Objective objective(
                int firstTeam,
                long firstDelta,
                int secondTeam,
                long secondDelta,
                TeamLine changedLine) {
            List<Double> overallAverages = new ArrayList<>(teamCount);
            List<double[]> lineAverages = new ArrayList<>(teamCount);
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
                overallAverages.add((double) total / counts[team]);

                double[] averages = emptyLineAverages();
                for (TeamLine line : TeamLine.values()) {
                    int count = lineCounts[team][line.ordinal()];
                    if (count == 0) {
                        continue;
                    }
                    long lineTotal = lineTotals[team][line.ordinal()];
                    if (line == changedLine) {
                        if (team == firstTeam) {
                            lineTotal += firstDelta;
                        }
                        if (team == secondTeam) {
                            lineTotal += secondDelta;
                        }
                    }
                    averages[line.ordinal()] = (double) lineTotal / count;
                }
                lineAverages.add(averages);
            }
            return Objective.of(overallAverages, lineAverages);
        }
    }

    private record Swap(MutableSlot left, MutableSlot right) {
    }

    private record Objective(
            int displayedOverallSpread,
            int displayedMatchupImbalance,
            int displayedMidfieldSpread,
            int maxDisplayedLineSpread,
            int totalDisplayedLineSpread,
            double rawOverallSpread,
            double rawMatchupImbalance,
            double rawMidfieldSpread,
            double totalRawLineSpread,
            double dispersion) {

        static Objective forTwoTeams(
                long teamOneScore,
                int teamOneCount,
                long teamTwoScore,
                int teamTwoCount,
                long[] teamOneLineScores,
                long[] totalLineScores,
                int[] teamOneLineCounts,
                int[] teamTwoLineCounts) {
            List<Double> overall = List.of(
                    (double) teamOneScore / teamOneCount,
                    (double) teamTwoScore / teamTwoCount);
            List<double[]> lines = new ArrayList<>(2);
            double[] first = emptyLineAverages();
            double[] second = emptyLineAverages();
            for (TeamLine line : TeamLine.values()) {
                int index = line.ordinal();
                if (teamOneLineCounts[index] > 0) {
                    first[index] = (double) teamOneLineScores[index]
                            / teamOneLineCounts[index];
                }
                if (teamTwoLineCounts[index] > 0) {
                    second[index] = (double) (totalLineScores[index] - teamOneLineScores[index])
                            / teamTwoLineCounts[index];
                }
            }
            lines.add(first);
            lines.add(second);
            return of(overall, lines);
        }

        static Objective of(
                List<Double> overallAverages,
                List<double[]> lineAverages) {
            if (overallAverages.isEmpty()) {
                return new Objective(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }

            int displayedOverallSpread = displayedSpread(overallAverages);
            double rawOverallSpread = rawSpread(overallAverages);
            double dispersion = dispersion(overallAverages);

            int displayedMatchupImbalance = 0;
            double rawMatchupImbalance = 0;
            int displayedMidfieldSpread = 0;
            double rawMidfieldSpread = 0;
            if (lineAverages.size() == 2) {
                double[] first = lineAverages.get(0);
                double[] second = lineAverages.get(1);
                double firstAttack = first[TeamLine.ATTACK.ordinal()];
                double secondAttack = second[TeamLine.ATTACK.ordinal()];
                double firstDefense = first[TeamLine.DEFENSE.ordinal()];
                double secondDefense = second[TeamLine.DEFENSE.ordinal()];
                if (allPresent(firstAttack, secondAttack, firstDefense, secondDefense)) {
                    int firstDisplayedAdvantage = rounded(firstAttack) - rounded(secondDefense);
                    int secondDisplayedAdvantage = rounded(secondAttack) - rounded(firstDefense);
                    displayedMatchupImbalance = Math.abs(
                            firstDisplayedAdvantage - secondDisplayedAdvantage);
                    double firstAdvantage = firstAttack - secondDefense;
                    double secondAdvantage = secondAttack - firstDefense;
                    rawMatchupImbalance = Math.abs(firstAdvantage - secondAdvantage);
                }

                double firstMidfield = first[TeamLine.MIDFIELD.ordinal()];
                double secondMidfield = second[TeamLine.MIDFIELD.ordinal()];
                if (allPresent(firstMidfield, secondMidfield)) {
                    displayedMidfieldSpread = Math.abs(
                            rounded(firstMidfield) - rounded(secondMidfield));
                    rawMidfieldSpread = Math.abs(firstMidfield - secondMidfield);
                }
            }

            int maxDisplayedLineSpread = 0;
            int totalDisplayedLineSpread = 0;
            double totalRawLineSpread = 0;
            for (TeamLine line : List.of(
                    TeamLine.DEFENSE,
                    TeamLine.MIDFIELD,
                    TeamLine.ATTACK)) {
                List<Double> values = new ArrayList<>(lineAverages.size());
                for (double[] team : lineAverages) {
                    double value = team[line.ordinal()];
                    if (!Double.isNaN(value)) {
                        values.add(value);
                    }
                }
                if (values.size() < 2) {
                    continue;
                }
                int displayed = displayedSpread(values);
                maxDisplayedLineSpread = Math.max(maxDisplayedLineSpread, displayed);
                totalDisplayedLineSpread += displayed;
                totalRawLineSpread += rawSpread(values);
            }

            return new Objective(
                    displayedOverallSpread,
                    displayedMatchupImbalance,
                    displayedMidfieldSpread,
                    maxDisplayedLineSpread,
                    totalDisplayedLineSpread,
                    rawOverallSpread,
                    rawMatchupImbalance,
                    rawMidfieldSpread,
                    totalRawLineSpread,
                    dispersion);
        }

        boolean isBetterThan(Objective other) {
            if (displayedOverallSpread != other.displayedOverallSpread) {
                return displayedOverallSpread < other.displayedOverallSpread;
            }
            if (displayedMatchupImbalance != other.displayedMatchupImbalance) {
                return displayedMatchupImbalance < other.displayedMatchupImbalance;
            }
            if (displayedMidfieldSpread != other.displayedMidfieldSpread) {
                return displayedMidfieldSpread < other.displayedMidfieldSpread;
            }
            if (maxDisplayedLineSpread != other.maxDisplayedLineSpread) {
                return maxDisplayedLineSpread < other.maxDisplayedLineSpread;
            }
            if (totalDisplayedLineSpread != other.totalDisplayedLineSpread) {
                return totalDisplayedLineSpread < other.totalDisplayedLineSpread;
            }
            int rawOverall = compare(rawOverallSpread, other.rawOverallSpread);
            if (rawOverall != 0) {
                return rawOverall < 0;
            }
            int rawMatchup = compare(rawMatchupImbalance, other.rawMatchupImbalance);
            if (rawMatchup != 0) {
                return rawMatchup < 0;
            }
            int rawMidfield = compare(rawMidfieldSpread, other.rawMidfieldSpread);
            if (rawMidfield != 0) {
                return rawMidfield < 0;
            }
            int rawLines = compare(totalRawLineSpread, other.totalRawLineSpread);
            if (rawLines != 0) {
                return rawLines < 0;
            }
            return dispersion < other.dispersion - EPSILON;
        }

        private static int compare(double left, double right) {
            if (left < right - EPSILON) {
                return -1;
            }
            if (left > right + EPSILON) {
                return 1;
            }
            return 0;
        }

        private static int displayedSpread(List<Double> averages) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (double average : averages) {
                int displayed = rounded(average);
                min = Math.min(min, displayed);
                max = Math.max(max, displayed);
            }
            return max - min;
        }

        private static double rawSpread(List<Double> averages) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double average : averages) {
                min = Math.min(min, average);
                max = Math.max(max, average);
            }
            return max - min;
        }

        private static double dispersion(List<Double> averages) {
            double mean = averages.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double value = 0;
            for (double average : averages) {
                double difference = average - mean;
                value += difference * difference;
            }
            return value;
        }

        private static int rounded(double value) {
            return (int) Math.round(value);
        }

        private static boolean allPresent(double... values) {
            for (double value : values) {
                if (Double.isNaN(value)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static double[] emptyLineAverages() {
        double[] averages = new double[TeamLine.values().length];
        java.util.Arrays.fill(averages, Double.NaN);
        return averages;
    }
}
