package com.onze.api.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Diversifies a technically optimized team split without allowing a meaningful
 * degradation of the balance already produced by {@link TeamBalanceOptimizer}.
 */
final class TeamDiversityOptimizer {

    private static final int MAX_EXACT_SLOTS = 30;
    private static final int MAX_BUCKET_OPTIONS = 20_000;
    private static final long MAX_CONFIGURATIONS = 200_000L;
    private static final int MAX_LOCAL_SWAPS = 64;
    private static final double EPSILON = 0.000_001;

    private TeamDiversityOptimizer() {
    }

    static Result diversify(
            List<TeamBalanceOptimizer.Slot> technicalBest,
            int teamCount,
            Context context) {
        List<TeamBalanceOptimizer.Slot> baseline = List.copyOf(technicalBest);
        if (baseline.size() < 2 || teamCount < 2 || context == null || !context.active()) {
            return new Result(baseline, false);
        }

        List<TeamBalanceOptimizer.Slot> selected = null;
        if (teamCount == 2 && baseline.size() <= MAX_EXACT_SLOTS) {
            selected = exactDiversify(baseline, context);
        }
        if (selected == null) {
            selected = localDiversify(baseline, teamCount, context);
        }

        String signature = normalizedDivision(selected, teamCount);
        boolean forcedRepeat = context.blockedDivisionSignatures().contains(signature);
        return new Result(List.copyOf(selected), forcedRepeat);
    }

    static String normalizedDivision(List<TeamBalanceOptimizer.Slot> slots, int teamCount) {
        Map<Integer, List<String>> byTeam = new TreeMap<>();
        for (int team = 1; team <= teamCount; team++) {
            byTeam.put(team, new ArrayList<>());
        }
        for (TeamBalanceOptimizer.Slot slot : slots) {
            byTeam.computeIfAbsent(slot.teamNumber(), ignored -> new ArrayList<>())
                    .add(slot.stableKey());
        }
        List<String> normalizedTeams = new ArrayList<>();
        for (List<String> members : byTeam.values()) {
            members.sort(String::compareTo);
            normalizedTeams.add(String.join(",", members));
        }
        normalizedTeams.sort(String::compareTo);
        return String.join("|", normalizedTeams);
    }

    static void accumulateTeammateWeights(
            Map<String, Double> target,
            List<TeamBalanceOptimizer.Slot> slots,
            double weight) {
        Map<Integer, List<String>> byTeam = new HashMap<>();
        for (TeamBalanceOptimizer.Slot slot : slots) {
            byTeam.computeIfAbsent(slot.teamNumber(), ignored -> new ArrayList<>())
                    .add(slot.stableKey());
        }
        for (List<String> members : byTeam.values()) {
            members.sort(String::compareTo);
            for (int left = 0; left < members.size(); left++) {
                for (int right = left + 1; right < members.size(); right++) {
                    target.merge(pairKey(members.get(left), members.get(right)), weight, Double::sum);
                }
            }
        }
    }

    private static List<TeamBalanceOptimizer.Slot> exactDiversify(
            List<TeamBalanceOptimizer.Slot> baseline,
            Context context) {
        Map<String, List<Integer>> grouped = new TreeMap<>();
        for (int index = 0; index < baseline.size(); index++) {
            TeamBalanceOptimizer.Slot slot = baseline.get(index);
            grouped.computeIfAbsent(slot.role() + "\u0000" + slot.estimated(), ignored -> new ArrayList<>())
                    .add(index);
        }

        List<Bucket> buckets = new ArrayList<>();
        for (List<Integer> indexes : grouped.values()) {
            indexes.sort(Comparator
                    .comparing((Integer index) -> baseline.get(index).stableKey())
                    .thenComparingInt(Integer::intValue));
            int requiredTeamOne = (int) indexes.stream()
                    .filter(index -> baseline.get(index).teamNumber() == 1)
                    .count();
            List<boolean[]> options = new ArrayList<>();
            collectOptions(indexes.size(), requiredTeamOne, 0, 0, new boolean[indexes.size()], options);
            if (options.isEmpty() || options.size() > MAX_BUCKET_OPTIONS) {
                return null;
            }
            buckets.add(new Bucket(List.copyOf(indexes), List.copyOf(options)));
        }

        ExactSearch search = new ExactSearch(
                baseline,
                buckets,
                technicalMetrics(baseline, 2),
                context);
        search.visit(0, teamNumbers(baseline));
        if (search.aborted || search.best == null) {
            return null;
        }
        return search.best;
    }

    private static void collectOptions(
            int size,
            int required,
            int index,
            int selected,
            boolean[] teamOne,
            List<boolean[]> options) {
        if (options.size() > MAX_BUCKET_OPTIONS) {
            return;
        }
        if (selected > required || selected + (size - index) < required) {
            return;
        }
        if (index == size) {
            if (selected == required) {
                options.add(teamOne.clone());
            }
            return;
        }
        teamOne[index] = true;
        collectOptions(size, required, index + 1, selected + 1, teamOne, options);
        teamOne[index] = false;
        collectOptions(size, required, index + 1, selected, teamOne, options);
    }

    private static List<TeamBalanceOptimizer.Slot> localDiversify(
            List<TeamBalanceOptimizer.Slot> baseline,
            int teamCount,
            Context context) {
        List<TeamBalanceOptimizer.Slot> current = new ArrayList<>(baseline);
        Metrics baselineMetrics = technicalMetrics(baseline, teamCount);
        DiversityScore currentScore = diversityScore(current, teamCount, context);

        for (int iteration = 0; iteration < MAX_LOCAL_SWAPS; iteration++) {
            List<TeamBalanceOptimizer.Slot> bestCandidate = null;
            DiversityScore bestScore = currentScore;
            for (int left = 0; left < current.size(); left++) {
                TeamBalanceOptimizer.Slot leftSlot = current.get(left);
                for (int right = left + 1; right < current.size(); right++) {
                    TeamBalanceOptimizer.Slot rightSlot = current.get(right);
                    if (leftSlot.teamNumber() == rightSlot.teamNumber()
                            || !leftSlot.role().equals(rightSlot.role())
                            || leftSlot.estimated() != rightSlot.estimated()) {
                        continue;
                    }
                    List<TeamBalanceOptimizer.Slot> candidate = swapTeams(current, left, right);
                    if (!technicalMetrics(candidate, teamCount).withinVariationToleranceOf(baselineMetrics)) {
                        continue;
                    }
                    DiversityScore candidateScore = diversityScore(candidate, teamCount, context);
                    if (candidateScore.isBetterThan(bestScore)) {
                        bestScore = candidateScore;
                        bestCandidate = candidate;
                    }
                }
            }
            if (bestCandidate == null) {
                break;
            }
            current = bestCandidate;
            currentScore = bestScore;
        }
        return current;
    }

    private static List<TeamBalanceOptimizer.Slot> swapTeams(
            List<TeamBalanceOptimizer.Slot> source,
            int leftIndex,
            int rightIndex) {
        List<TeamBalanceOptimizer.Slot> result = new ArrayList<>(source);
        TeamBalanceOptimizer.Slot left = source.get(leftIndex);
        TeamBalanceOptimizer.Slot right = source.get(rightIndex);
        result.set(leftIndex, withTeam(left, right.teamNumber()));
        result.set(rightIndex, withTeam(right, left.teamNumber()));
        return result;
    }

    private static TeamBalanceOptimizer.Slot withTeam(TeamBalanceOptimizer.Slot slot, int teamNumber) {
        return new TeamBalanceOptimizer.Slot(
                slot.index(), teamNumber, slot.role(), slot.score(), slot.estimated(), slot.stableKey());
    }

    private static int[] teamNumbers(List<TeamBalanceOptimizer.Slot> slots) {
        int[] teams = new int[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            teams[index] = slots.get(index).teamNumber();
        }
        return teams;
    }

    private static List<TeamBalanceOptimizer.Slot> applyTeams(
            List<TeamBalanceOptimizer.Slot> source,
            int[] teams) {
        List<TeamBalanceOptimizer.Slot> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            result.add(withTeam(source.get(index), teams[index]));
        }
        return result;
    }

    private static DiversityScore diversityScore(
            List<TeamBalanceOptimizer.Slot> slots,
            int teamCount,
            Context context) {
        String signature = normalizedDivision(slots, teamCount);
        int blocked = context.blockedDivisionSignatures().contains(signature) ? 1 : 0;
        double teammatePenalty = 0;
        Map<Integer, List<String>> byTeam = new HashMap<>();
        for (TeamBalanceOptimizer.Slot slot : slots) {
            byTeam.computeIfAbsent(slot.teamNumber(), ignored -> new ArrayList<>())
                    .add(slot.stableKey());
        }
        for (List<String> members : byTeam.values()) {
            members.sort(String::compareTo);
            for (int left = 0; left < members.size(); left++) {
                for (int right = left + 1; right < members.size(); right++) {
                    teammatePenalty += context.teammatePairWeights()
                            .getOrDefault(pairKey(members.get(left), members.get(right)), 0.0);
                }
            }
        }
        long tieBreaker = Integer.toUnsignedLong((signature + "#" + context.seed()).hashCode());
        return new DiversityScore(blocked, teammatePenalty, tieBreaker);
    }

    private static String pairKey(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "\u0000" + right : right + "\u0000" + left;
    }

    private static Metrics technicalMetrics(List<TeamBalanceOptimizer.Slot> slots, int teamCount) {
        long[] totals = new long[teamCount + 1];
        int[] counts = new int[teamCount + 1];
        long[][] lineTotals = new long[teamCount + 1][Line.values().length];
        int[][] lineCounts = new int[teamCount + 1][Line.values().length];
        for (TeamBalanceOptimizer.Slot slot : slots) {
            int team = slot.teamNumber();
            if (team < 1 || team > teamCount) {
                continue;
            }
            int line = lineForRole(slot.role()).ordinal();
            totals[team] += slot.score();
            counts[team]++;
            lineTotals[team][line] += slot.score();
            lineCounts[team][line]++;
        }

        List<Double> overall = new ArrayList<>();
        List<double[]> lines = new ArrayList<>();
        for (int team = 1; team <= teamCount; team++) {
            if (counts[team] == 0) {
                continue;
            }
            overall.add((double) totals[team] / counts[team]);
            double[] averages = new double[Line.values().length];
            java.util.Arrays.fill(averages, Double.NaN);
            for (Line line : Line.values()) {
                int count = lineCounts[team][line.ordinal()];
                if (count > 0) {
                    averages[line.ordinal()] = (double) lineTotals[team][line.ordinal()] / count;
                }
            }
            lines.add(averages);
        }

        int displayedOverall = displayedSpread(overall);
        double rawOverall = rawSpread(overall);
        int matchup = 0;
        int midfield = 0;
        if (lines.size() == 2) {
            double[] first = lines.get(0);
            double[] second = lines.get(1);
            double firstAttack = first[Line.ATTACK.ordinal()];
            double secondAttack = second[Line.ATTACK.ordinal()];
            double firstDefense = first[Line.DEFENSE.ordinal()];
            double secondDefense = second[Line.DEFENSE.ordinal()];
            if (allPresent(firstAttack, secondAttack, firstDefense, secondDefense)) {
                int firstAdvantage = rounded(firstAttack) - rounded(secondDefense);
                int secondAdvantage = rounded(secondAttack) - rounded(firstDefense);
                matchup = Math.abs(firstAdvantage - secondAdvantage);
            }
            double firstMidfield = first[Line.MIDFIELD.ordinal()];
            double secondMidfield = second[Line.MIDFIELD.ordinal()];
            if (allPresent(firstMidfield, secondMidfield)) {
                midfield = Math.abs(rounded(firstMidfield) - rounded(secondMidfield));
            }
        }

        int maxLine = 0;
        int totalLine = 0;
        for (Line line : List.of(Line.DEFENSE, Line.MIDFIELD, Line.ATTACK)) {
            List<Double> values = new ArrayList<>();
            for (double[] team : lines) {
                double value = team[line.ordinal()];
                if (!Double.isNaN(value)) {
                    values.add(value);
                }
            }
            if (values.size() >= 2) {
                int spread = displayedSpread(values);
                maxLine = Math.max(maxLine, spread);
                totalLine += spread;
            }
        }
        return new Metrics(displayedOverall, matchup, midfield, maxLine, totalLine, rawOverall);
    }

    private static int displayedSpread(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (double value : values) {
            int rounded = rounded(value);
            min = Math.min(min, rounded);
            max = Math.max(max, rounded);
        }
        return max - min;
    }

    private static double rawSpread(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
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

    private static Line lineForRole(String role) {
        return switch (role) {
            case "DEFENDER", "RIGHT_DEFENDER", "LEFT_DEFENDER", "CENTER_DEFENDER",
                    "RIGHT_BACK", "LEFT_BACK", "FIXO" -> Line.DEFENSE;
            case "DEFENSIVE_MIDFIELDER", "MIDFIELDER", "RIGHT_MIDFIELDER",
                    "LEFT_MIDFIELDER", "CENTRAL_MIDFIELDER", "PLAYMAKER",
                    "RIGHT_WINGER_FUTSAL", "LEFT_WINGER_FUTSAL" -> Line.MIDFIELD;
            case "ATTACKER", "RIGHT_WINGER", "LEFT_WINGER", "CENTER_FORWARD", "PIVOT" -> Line.ATTACK;
            default -> Line.OTHER;
        };
    }

    record Context(
            Set<String> blockedDivisionSignatures,
            Map<String, Double> teammatePairWeights,
            long seed) {
        Context {
            blockedDivisionSignatures = Set.copyOf(blockedDivisionSignatures);
            teammatePairWeights = Map.copyOf(teammatePairWeights);
        }

        boolean active() {
            return !blockedDivisionSignatures.isEmpty() || !teammatePairWeights.isEmpty();
        }
    }

    record Result(List<TeamBalanceOptimizer.Slot> slots, boolean forcedRepeat) {
    }

    private record Bucket(List<Integer> indexes, List<boolean[]> options) {
    }

    private record DiversityScore(int blocked, double teammatePenalty, long tieBreaker) {
        boolean isBetterThan(DiversityScore other) {
            if (blocked != other.blocked) {
                return blocked < other.blocked;
            }
            if (teammatePenalty < other.teammatePenalty - EPSILON) {
                return true;
            }
            if (teammatePenalty > other.teammatePenalty + EPSILON) {
                return false;
            }
            return tieBreaker < other.tieBreaker;
        }
    }

    private record Metrics(
            int displayedOverallSpread,
            int displayedMatchupImbalance,
            int displayedMidfieldSpread,
            int maxDisplayedLineSpread,
            int totalDisplayedLineSpread,
            double rawOverallSpread) {
        boolean withinVariationToleranceOf(Metrics baseline) {
            return displayedOverallSpread <= baseline.displayedOverallSpread + 1
                    && displayedMatchupImbalance <= baseline.displayedMatchupImbalance + 1
                    && displayedMidfieldSpread <= baseline.displayedMidfieldSpread + 1
                    && maxDisplayedLineSpread <= baseline.maxDisplayedLineSpread + 1
                    && totalDisplayedLineSpread <= baseline.totalDisplayedLineSpread + 2
                    && rawOverallSpread <= baseline.rawOverallSpread + 1.0 + EPSILON;
        }
    }

    private enum Line {
        DEFENSE,
        MIDFIELD,
        ATTACK,
        OTHER
    }

    private static final class ExactSearch {
        private final List<TeamBalanceOptimizer.Slot> baseline;
        private final List<Bucket> buckets;
        private final Metrics baselineMetrics;
        private final Context context;
        private long visited;
        private boolean aborted;
        private DiversityScore bestScore;
        private List<TeamBalanceOptimizer.Slot> best;

        ExactSearch(
                List<TeamBalanceOptimizer.Slot> baseline,
                List<Bucket> buckets,
                Metrics baselineMetrics,
                Context context) {
            this.baseline = baseline;
            this.buckets = buckets;
            this.baselineMetrics = baselineMetrics;
            this.context = context;
        }

        void visit(int bucketIndex, int[] teams) {
            if (aborted) {
                return;
            }
            if (bucketIndex == buckets.size()) {
                visited++;
                if (visited > MAX_CONFIGURATIONS) {
                    aborted = true;
                    return;
                }
                List<TeamBalanceOptimizer.Slot> candidate = applyTeams(baseline, teams);
                if (!technicalMetrics(candidate, 2).withinVariationToleranceOf(baselineMetrics)) {
                    return;
                }
                DiversityScore candidateScore = diversityScore(candidate, 2, context);
                if (bestScore == null || candidateScore.isBetterThan(bestScore)) {
                    bestScore = candidateScore;
                    best = candidate;
                }
                return;
            }

            Bucket bucket = buckets.get(bucketIndex);
            int[] original = teams.clone();
            for (boolean[] option : bucket.options()) {
                for (int index = 0; index < bucket.indexes().size(); index++) {
                    teams[bucket.indexes().get(index)] = option[index] ? 1 : 2;
                }
                visit(bucketIndex + 1, teams);
                if (aborted) {
                    return;
                }
                System.arraycopy(original, 0, teams, 0, teams.length);
            }
        }
    }
}
