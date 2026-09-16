package com.onze.api.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.onze.api.match.TeamModels.MatchTeamsResponse;
import com.onze.api.match.TeamModels.TeamAssignmentResponse;
import com.onze.api.match.TeamModels.TeamResponse;

/**
 * Chooses a different team division only inside a narrow technical tolerance.
 * Positions and score-source buckets are preserved, so diversity never changes
 * the role assigned by {@link MatchTeamService}; it only changes team numbers.
 */
final class TeamDiversityOptimizer {

    private static final int MAX_CANDIDATES = 5_000;
    private static final int OVERALL_DISPLAY_TOLERANCE = 1;
    private static final int MATCHUP_DISPLAY_TOLERANCE = 1;
    private static final int MIDFIELD_DISPLAY_TOLERANCE = 1;
    private static final int MAX_LINE_DISPLAY_TOLERANCE = 1;
    private static final int TOTAL_LINE_DISPLAY_TOLERANCE = 2;
    private static final double RAW_OVERALL_TOLERANCE = 1.0;
    private static final double RAW_MATCHUP_TOLERANCE = 1.5;
    private static final double RAW_MIDFIELD_TOLERANCE = 1.5;
    private static final double RAW_LINES_TOLERANCE = 3.0;
    private static final long CURRENT_EXACT_PENALTY = 1_000_000L;
    private static final long RECENT_EXACT_PENALTY = 100_000L;

    private TeamDiversityOptimizer() {
    }

    static Decision choose(
            MatchTeamsResponse generated,
            String previousSignature,
            List<String> currentOccurrenceSignatures,
            List<WeightedHistory> recentHistory,
            UUID matchId,
            int generationNumber) {
        List<Slot> slots = slots(generated);
        if (slots.size() < 2 || generated.teamCount() < 2) {
            String signature = signature(generated);
            return new Decision(teamMap(slots, baseline(slots)), signature, false,
                    Objects.equals(signature, previousSignature),
                    containsRecent(signature, recentHistory));
        }

        int[] baseline = baseline(slots);
        TechnicalMetrics baselineMetrics = metrics(slots, baseline, generated.teamCount());
        LinkedHashMap<String, int[]> candidates = candidates(slots, baseline);

        Set<String> currentSignatures = new HashSet<>(currentOccurrenceSignatures);
        if (previousSignature != null) {
            currentSignatures.add(previousSignature);
        }

        Map<String, Integer> historicalPairWeights = pairWeights(
                currentOccurrenceSignatures,
                recentHistory);

        CandidateChoice best = null;
        for (Map.Entry<String, int[]> entry : candidates.entrySet()) {
            TechnicalMetrics technical = metrics(slots, entry.getValue(), generated.teamCount());
            if (!technical.acceptableComparedTo(baselineMetrics)) {
                continue;
            }
            String candidateSignature = entry.getKey();
            long exactPenalty = exactPenalty(
                    candidateSignature,
                    currentSignatures,
                    recentHistory);
            long pairPenalty = pairPenalty(candidateSignature, historicalPairWeights);
            long tie = Integer.toUnsignedLong(Objects.hash(
                    matchId, generationNumber, candidateSignature));
            CandidateChoice candidate = new CandidateChoice(
                    candidateSignature,
                    entry.getValue(),
                    exactPenalty,
                    pairPenalty,
                    technical,
                    tie);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }

        if (best == null) {
            String signature = signature(slots, baseline);
            return new Decision(teamMap(slots, baseline), signature, false,
                    Objects.equals(signature, previousSignature),
                    containsRecent(signature, recentHistory));
        }

        boolean changedFromGenerated = !Arrays.equals(baseline, best.teams());
        return new Decision(
                teamMap(slots, best.teams()),
                best.signature(),
                changedFromGenerated,
                Objects.equals(best.signature(), previousSignature),
                containsRecent(best.signature(), recentHistory));
    }

    static String signature(MatchTeamsResponse response) {
        if (response == null) {
            return null;
        }
        List<String> teams = new ArrayList<>();
        for (TeamResponse team : response.teams()) {
            List<String> members = team.assignments().stream()
                    .map(TeamDiversityOptimizer::participantKey)
                    .sorted()
                    .toList();
            if (!members.isEmpty()) {
                teams.add(String.join(",", members));
            }
        }
        if (teams.isEmpty()) {
            return null;
        }
        teams.sort(String::compareTo);
        return String.join("|", teams);
    }

    private static List<Slot> slots(MatchTeamsResponse response) {
        List<Slot> result = new ArrayList<>();
        for (TeamResponse team : response.teams()) {
            for (TeamAssignmentResponse assignment : team.assignments()) {
                result.add(new Slot(
                        participantKey(assignment),
                        team.teamNumber(),
                        assignment.assignedRole(),
                        assignment.scoreSource() == null ? "UNKNOWN" : assignment.scoreSource().name(),
                        assignment.overallUsed() == null ? 25 : assignment.overallUsed(),
                        lineForRole(assignment.assignedRole())));
            }
        }
        result.sort(Comparator.comparing(Slot::participantKey));
        return List.copyOf(result);
    }

    private static String participantKey(TeamAssignmentResponse assignment) {
        return assignment.participantType().name() + ":" + assignment.participantId();
    }

    private static int[] baseline(List<Slot> slots) {
        int[] result = new int[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            result[index] = slots.get(index).teamNumber();
        }
        return result;
    }

    private static LinkedHashMap<String, int[]> candidates(List<Slot> slots, int[] baseline) {
        LinkedHashMap<String, int[]> result = new LinkedHashMap<>();
        addCandidate(result, slots, baseline);

        List<Swap> swaps = new ArrayList<>();
        for (int left = 0; left < slots.size(); left++) {
            for (int right = left + 1; right < slots.size(); right++) {
                Slot first = slots.get(left);
                Slot second = slots.get(right);
                if (first.teamNumber() == second.teamNumber()
                        || !first.role().equals(second.role())
                        || !first.scoreSource().equals(second.scoreSource())) {
                    continue;
                }
                swaps.add(new Swap(left, right));
                int[] candidate = baseline.clone();
                applySwap(candidate, left, right);
                addCandidate(result, slots, candidate);
                if (result.size() >= MAX_CANDIDATES) {
                    return result;
                }
            }
        }

        for (int firstSwap = 0; firstSwap < swaps.size(); firstSwap++) {
            Swap first = swaps.get(firstSwap);
            for (int secondSwap = firstSwap + 1; secondSwap < swaps.size(); secondSwap++) {
                Swap second = swaps.get(secondSwap);
                if (first.overlaps(second)) {
                    continue;
                }
                int[] candidate = baseline.clone();
                applySwap(candidate, first.left(), first.right());
                applySwap(candidate, second.left(), second.right());
                addCandidate(result, slots, candidate);
                if (result.size() >= MAX_CANDIDATES) {
                    return result;
                }
            }
        }
        return result;
    }

    private static void addCandidate(
            LinkedHashMap<String, int[]> candidates,
            List<Slot> slots,
            int[] teams) {
        candidates.putIfAbsent(signature(slots, teams), teams.clone());
    }

    private static void applySwap(int[] teams, int left, int right) {
        int team = teams[left];
        teams[left] = teams[right];
        teams[right] = team;
    }

    private static String signature(List<Slot> slots, int[] teams) {
        Map<Integer, List<String>> byTeam = new HashMap<>();
        for (int index = 0; index < slots.size(); index++) {
            byTeam.computeIfAbsent(teams[index], ignored -> new ArrayList<>())
                    .add(slots.get(index).participantKey());
        }
        List<String> teamSignatures = new ArrayList<>();
        for (List<String> members : byTeam.values()) {
            members.sort(String::compareTo);
            teamSignatures.add(String.join(",", members));
        }
        teamSignatures.sort(String::compareTo);
        return String.join("|", teamSignatures);
    }

    private static Map<String, Integer> teamMap(List<Slot> slots, int[] teams) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < slots.size(); index++) {
            result.put(slots.get(index).participantKey(), teams[index]);
        }
        return Map.copyOf(result);
    }

    private static long exactPenalty(
            String signature,
            Set<String> currentSignatures,
            List<WeightedHistory> recentHistory) {
        long penalty = currentSignatures.contains(signature) ? CURRENT_EXACT_PENALTY : 0;
        for (WeightedHistory history : recentHistory) {
            if (signature.equals(history.signature())) {
                penalty += RECENT_EXACT_PENALTY * history.weight();
            }
        }
        return penalty;
    }

    private static boolean containsRecent(
            String signature,
            List<WeightedHistory> recentHistory) {
        if (signature == null) {
            return false;
        }
        return recentHistory.stream().anyMatch(history -> signature.equals(history.signature()));
    }

    private static Map<String, Integer> pairWeights(
            List<String> currentOccurrenceSignatures,
            List<WeightedHistory> recentHistory) {
        Map<String, Integer> result = new HashMap<>();
        int currentWeight = Math.max(6, currentOccurrenceSignatures.size() + 5);
        for (String signature : currentOccurrenceSignatures) {
            addPairs(result, signature, currentWeight--);
        }
        for (WeightedHistory history : recentHistory) {
            addPairs(result, history.signature(), history.weight());
        }
        return result;
    }

    private static void addPairs(Map<String, Integer> weights, String signature, int weight) {
        for (String pair : pairs(signature)) {
            weights.merge(pair, weight, Integer::sum);
        }
    }

    private static long pairPenalty(String signature, Map<String, Integer> weights) {
        long penalty = 0;
        for (String pair : pairs(signature)) {
            penalty += weights.getOrDefault(pair, 0);
        }
        return penalty;
    }

    private static Set<String> pairs(String signature) {
        Set<String> result = new HashSet<>();
        if (signature == null || signature.isBlank()) {
            return result;
        }
        for (String team : signature.split("\\|")) {
            if (team.isBlank()) {
                continue;
            }
            String[] members = team.split(",");
            Arrays.sort(members);
            for (int left = 0; left < members.length; left++) {
                for (int right = left + 1; right < members.length; right++) {
                    result.add(members[left] + "~" + members[right]);
                }
            }
        }
        return result;
    }

    private static TechnicalMetrics metrics(List<Slot> slots, int[] teams, int teamCount) {
        long[] totals = new long[teamCount + 1];
        int[] counts = new int[teamCount + 1];
        long[][] lineTotals = new long[teamCount + 1][TeamLine.values().length];
        int[][] lineCounts = new int[teamCount + 1][TeamLine.values().length];

        for (int index = 0; index < slots.size(); index++) {
            int team = teams[index];
            if (team < 1 || team > teamCount) {
                continue;
            }
            Slot slot = slots.get(index);
            totals[team] += slot.score();
            counts[team]++;
            int line = slot.line().ordinal();
            lineTotals[team][line] += slot.score();
            lineCounts[team][line]++;
        }

        List<Double> overall = new ArrayList<>();
        double[][] lines = new double[teamCount + 1][TeamLine.values().length];
        for (double[] values : lines) {
            Arrays.fill(values, Double.NaN);
        }
        for (int team = 1; team <= teamCount; team++) {
            if (counts[team] == 0) {
                continue;
            }
            overall.add((double) totals[team] / counts[team]);
            for (TeamLine line : TeamLine.values()) {
                int count = lineCounts[team][line.ordinal()];
                if (count > 0) {
                    lines[team][line.ordinal()] =
                            (double) lineTotals[team][line.ordinal()] / count;
                }
            }
        }

        int displayedOverall = displayedSpread(overall);
        double rawOverall = rawSpread(overall);
        double dispersion = dispersion(overall);
        int displayedMatchup = 0;
        double rawMatchup = 0;
        int displayedMidfield = 0;
        double rawMidfield = 0;

        if (teamCount == 2 && counts[1] > 0 && counts[2] > 0) {
            double firstAttack = lines[1][TeamLine.ATTACK.ordinal()];
            double secondAttack = lines[2][TeamLine.ATTACK.ordinal()];
            double firstDefense = lines[1][TeamLine.DEFENSE.ordinal()];
            double secondDefense = lines[2][TeamLine.DEFENSE.ordinal()];
            if (allPresent(firstAttack, secondAttack, firstDefense, secondDefense)) {
                displayedMatchup = Math.abs(
                        (rounded(firstAttack) - rounded(secondDefense))
                                - (rounded(secondAttack) - rounded(firstDefense)));
                rawMatchup = Math.abs(
                        (firstAttack - secondDefense)
                                - (secondAttack - firstDefense));
            }
            double firstMidfield = lines[1][TeamLine.MIDFIELD.ordinal()];
            double secondMidfield = lines[2][TeamLine.MIDFIELD.ordinal()];
            if (allPresent(firstMidfield, secondMidfield)) {
                displayedMidfield = Math.abs(rounded(firstMidfield) - rounded(secondMidfield));
                rawMidfield = Math.abs(firstMidfield - secondMidfield);
            }
        }

        int maxDisplayedLine = 0;
        int totalDisplayedLine = 0;
        double totalRawLine = 0;
        for (TeamLine line : List.of(TeamLine.DEFENSE, TeamLine.MIDFIELD, TeamLine.ATTACK)) {
            List<Double> values = new ArrayList<>();
            for (int team = 1; team <= teamCount; team++) {
                double value = lines[team][line.ordinal()];
                if (!Double.isNaN(value)) {
                    values.add(value);
                }
            }
            if (values.size() < 2) {
                continue;
            }
            int displayed = displayedSpread(values);
            maxDisplayedLine = Math.max(maxDisplayedLine, displayed);
            totalDisplayedLine += displayed;
            totalRawLine += rawSpread(values);
        }

        return new TechnicalMetrics(
                displayedOverall,
                displayedMatchup,
                displayedMidfield,
                maxDisplayedLine,
                totalDisplayedLine,
                rawOverall,
                rawMatchup,
                rawMidfield,
                totalRawLine,
                dispersion);
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
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return max - min;
    }

    private static double dispersion(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream()
                .mapToDouble(value -> {
                    double delta = value - average;
                    return delta * delta;
                })
                .sum();
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

    record WeightedHistory(String signature, int weight) {
    }

    record Decision(
            Map<String, Integer> teamByParticipant,
            String signature,
            boolean changedFromGenerated,
            boolean repeatedPrevious,
            boolean repeatedRecent) {
    }

    private record Slot(
            String participantKey,
            int teamNumber,
            String role,
            String scoreSource,
            int score,
            TeamLine line) {
    }

    private record Swap(int left, int right) {
        boolean overlaps(Swap other) {
            return left == other.left || left == other.right
                    || right == other.left || right == other.right;
        }
    }

    private enum TeamLine {
        DEFENSE,
        MIDFIELD,
        ATTACK,
        OTHER
    }

    private record TechnicalMetrics(
            int displayedOverallSpread,
            int displayedMatchupImbalance,
            int displayedMidfieldSpread,
            int maxDisplayedLineSpread,
            int totalDisplayedLineSpread,
            double rawOverallSpread,
            double rawMatchupImbalance,
            double rawMidfieldSpread,
            double totalRawLineSpread,
            double dispersion) implements Comparable<TechnicalMetrics> {

        boolean acceptableComparedTo(TechnicalMetrics baseline) {
            return displayedOverallSpread <= baseline.displayedOverallSpread + OVERALL_DISPLAY_TOLERANCE
                    && displayedMatchupImbalance <= baseline.displayedMatchupImbalance + MATCHUP_DISPLAY_TOLERANCE
                    && displayedMidfieldSpread <= baseline.displayedMidfieldSpread + MIDFIELD_DISPLAY_TOLERANCE
                    && maxDisplayedLineSpread <= baseline.maxDisplayedLineSpread + MAX_LINE_DISPLAY_TOLERANCE
                    && totalDisplayedLineSpread <= baseline.totalDisplayedLineSpread + TOTAL_LINE_DISPLAY_TOLERANCE
                    && rawOverallSpread <= baseline.rawOverallSpread + RAW_OVERALL_TOLERANCE
                    && rawMatchupImbalance <= baseline.rawMatchupImbalance + RAW_MATCHUP_TOLERANCE
                    && rawMidfieldSpread <= baseline.rawMidfieldSpread + RAW_MIDFIELD_TOLERANCE
                    && totalRawLineSpread <= baseline.totalRawLineSpread + RAW_LINES_TOLERANCE;
        }

        @Override
        public int compareTo(TechnicalMetrics other) {
            int value = Integer.compare(displayedOverallSpread, other.displayedOverallSpread);
            if (value != 0) return value;
            value = Integer.compare(displayedMatchupImbalance, other.displayedMatchupImbalance);
            if (value != 0) return value;
            value = Integer.compare(displayedMidfieldSpread, other.displayedMidfieldSpread);
            if (value != 0) return value;
            value = Integer.compare(maxDisplayedLineSpread, other.maxDisplayedLineSpread);
            if (value != 0) return value;
            value = Integer.compare(totalDisplayedLineSpread, other.totalDisplayedLineSpread);
            if (value != 0) return value;
            value = Double.compare(rawOverallSpread, other.rawOverallSpread);
            if (value != 0) return value;
            value = Double.compare(rawMatchupImbalance, other.rawMatchupImbalance);
            if (value != 0) return value;
            value = Double.compare(rawMidfieldSpread, other.rawMidfieldSpread);
            if (value != 0) return value;
            value = Double.compare(totalRawLineSpread, other.totalRawLineSpread);
            if (value != 0) return value;
            return Double.compare(dispersion, other.dispersion);
        }
    }

    private record CandidateChoice(
            String signature,
            int[] teams,
            long exactPenalty,
            long pairPenalty,
            TechnicalMetrics technical,
            long seededTie) implements Comparable<CandidateChoice> {

        @Override
        public int compareTo(CandidateChoice other) {
            int value = Long.compare(exactPenalty, other.exactPenalty);
            if (value != 0) return value;
            value = Long.compare(pairPenalty, other.pairPenalty);
            if (value != 0) return value;
            value = technical.compareTo(other.technical);
            if (value != 0) return value;
            return Long.compare(seededTie, other.seededTie);
        }
    }
}
