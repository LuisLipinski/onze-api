package com.onze.api.technical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.onze.api.group.PlayerPosition;

/**
 * Single source of truth for technical score weights. A stored rating unit is
 * half a star and therefore contributes five points on the 0-50 scale.
 */
public final class TechnicalRatingPolicy {

    private static final double CHARACTERISTIC_WEIGHT = 85.0;
    private static final double COMPLEMENTARY_WEIGHT = 15.0;

    private static final Set<PlayerSkill> GOALKEEPER_SKILLS = EnumSet.of(
            PlayerSkill.GOALKEEPER_REFLEXES,
            PlayerSkill.GOALKEEPER_POSITIONING,
            PlayerSkill.GOALKEEPER_RUSHING_OUT);
    private static final Set<PlayerSkill> LINE_SKILLS = EnumSet.complementOf(
            EnumSet.copyOf(GOALKEEPER_SKILLS));
    private static final Set<PlayerSkill> GOALKEEPER_COMPLEMENTS = EnumSet.of(
            PlayerSkill.PASSING,
            PlayerSkill.LONG_PASSING,
            PlayerSkill.BALL_CONTROL,
            PlayerSkill.AGILITY);

    private static final Map<PlayerPosition, Map<PlayerSkill, Double>> POSITION_WEIGHTS = positionWeights();
    private static final Map<FutsalRole, Map<PlayerSkill, Double>> FUTSAL_WEIGHTS = futsalWeights();

    private TechnicalRatingPolicy() {
    }

    public static OverallResult general(Map<PlayerSkill, Integer> ratings) {
        double knownScore = 0;
        int known = 0;
        for (PlayerSkill skill : LINE_SKILLS) {
            Integer rating = ratings.get(skill);
            if (rating != null) {
                knownScore += rating * 5.0;
                known++;
            }
        }
        Integer overall = known == 0 ? null : (int) Math.round(knownScore / known);
        int coverage = (int) Math.round(known * 100.0 / LINE_SKILLS.size());
        return new OverallResult(
                overall, coverage, known == LINE_SKILLS.size(),
                known < LINE_SKILLS.size(), null, List.of());
    }

    public static OverallResult position(Map<PlayerSkill, Integer> ratings, PlayerPosition position) {
        if (position == PlayerPosition.DEFENDER) {
            return bestPosition(ratings, List.of(
                    PlayerPosition.RIGHT_DEFENDER,
                    PlayerPosition.LEFT_DEFENDER,
                    PlayerPosition.CENTER_DEFENDER,
                    PlayerPosition.RIGHT_BACK,
                    PlayerPosition.LEFT_BACK));
        }
        if (position == PlayerPosition.MIDFIELDER) {
            return bestPosition(ratings, List.of(
                    PlayerPosition.DEFENSIVE_MIDFIELDER,
                    PlayerPosition.RIGHT_MIDFIELDER,
                    PlayerPosition.LEFT_MIDFIELDER,
                    PlayerPosition.CENTRAL_MIDFIELDER,
                    PlayerPosition.PLAYMAKER));
        }
        if (position == PlayerPosition.ATTACKER) {
            return bestPosition(ratings, List.of(
                    PlayerPosition.RIGHT_WINGER,
                    PlayerPosition.LEFT_WINGER,
                    PlayerPosition.CENTER_FORWARD));
        }
        Map<PlayerSkill, Double> characteristicWeights = POSITION_WEIGHTS.get(position);
        Set<PlayerSkill> complements = position == PlayerPosition.GOALKEEPER
                ? GOALKEEPER_COMPLEMENTS
                : difference(LINE_SKILLS, characteristicWeights.keySet());
        return weighted(ratings, characteristicWeights, complements, position);
    }

    public static OverallResult futsal(Map<PlayerSkill, Integer> ratings, FutsalRole role) {
        Map<PlayerSkill, Double> characteristicWeights = FUTSAL_WEIGHTS.get(role);
        Set<PlayerSkill> complements = role == FutsalRole.GOALKEEPER
                ? difference(GOALKEEPER_COMPLEMENTS, characteristicWeights.keySet())
                : difference(LINE_SKILLS, characteristicWeights.keySet());
        return weighted(ratings, characteristicWeights, complements, null);
    }

    public static Set<PlayerSkill> essentialSkills(PlayerPosition position) {
        if (position == PlayerPosition.DEFENDER) {
            return combinedEssentials(List.of(
                    PlayerPosition.RIGHT_DEFENDER,
                    PlayerPosition.LEFT_DEFENDER,
                    PlayerPosition.CENTER_DEFENDER,
                    PlayerPosition.RIGHT_BACK,
                    PlayerPosition.LEFT_BACK));
        }
        if (position == PlayerPosition.MIDFIELDER) {
            return combinedEssentials(List.of(
                    PlayerPosition.DEFENSIVE_MIDFIELDER,
                    PlayerPosition.RIGHT_MIDFIELDER,
                    PlayerPosition.LEFT_MIDFIELDER,
                    PlayerPosition.CENTRAL_MIDFIELDER,
                    PlayerPosition.PLAYMAKER));
        }
        if (position == PlayerPosition.ATTACKER) {
            return combinedEssentials(List.of(
                    PlayerPosition.RIGHT_WINGER,
                    PlayerPosition.LEFT_WINGER,
                    PlayerPosition.CENTER_FORWARD));
        }
        return Set.copyOf(POSITION_WEIGHTS.get(position).keySet());
    }

    public static Set<PlayerSkill> essentialSkills(FutsalRole role) {
        return Set.copyOf(FUTSAL_WEIGHTS.get(role).keySet());
    }

    private static OverallResult bestPosition(
            Map<PlayerSkill, Integer> ratings,
            List<PlayerPosition> specializations) {
        return specializations.stream()
                .map(position -> {
                    Map<PlayerSkill, Double> characteristicWeights = POSITION_WEIGHTS.get(position);
                    return weighted(
                            ratings,
                            characteristicWeights,
                            difference(LINE_SKILLS, characteristicWeights.keySet()),
                            position);
                })
                .max((left, right) -> {
                    int leftScore = left.overall() == null ? -1 : left.overall();
                    int rightScore = right.overall() == null ? -1 : right.overall();
                    int score = Integer.compare(leftScore, rightScore);
                    return score != 0 ? score : Integer.compare(left.coverage(), right.coverage());
                })
                .orElse(new OverallResult(null, 0, false, true, null, List.of()));
    }

    private static OverallResult weighted(
            Map<PlayerSkill, Integer> ratings,
            Map<PlayerSkill, Double> characteristicWeights,
            Set<PlayerSkill> complements,
            PlayerPosition resolvedPosition) {
        double complementWeight = complements.isEmpty()
                ? 0
                : COMPLEMENTARY_WEIGHT / complements.size();
        double knownWeight = 0;
        double weightedScore = 0;
        List<PlayerSkill> missing = new ArrayList<>();

        for (Map.Entry<PlayerSkill, Double> entry : characteristicWeights.entrySet()) {
            PlayerSkill skill = entry.getKey();
            Integer rating = ratings.get(skill);
            if (rating == null) {
                missing.add(skill);
            } else {
                knownWeight += entry.getValue();
                weightedScore += rating * 5.0 * entry.getValue();
            }
        }
        for (PlayerSkill skill : complements) {
            Integer rating = ratings.get(skill);
            if (rating != null) {
                knownWeight += complementWeight;
                weightedScore += rating * 5.0 * complementWeight;
            }
        }

        int coverage = (int) Math.round(knownWeight);
        Integer overall = knownWeight == 0
                ? null
                : (int) Math.round(weightedScore / knownWeight);
        boolean reliable = missing.isEmpty() && !characteristicWeights.isEmpty();
        return new OverallResult(
                overall,
                Math.min(100, coverage),
                reliable,
                !reliable,
                resolvedPosition,
                List.copyOf(missing));
    }

    private static Set<PlayerSkill> difference(Set<PlayerSkill> source, Set<PlayerSkill> excluded) {
        EnumSet<PlayerSkill> result = EnumSet.copyOf(source);
        result.removeAll(excluded);
        return result;
    }

    private static Set<PlayerSkill> combinedEssentials(List<PlayerPosition> positions) {
        EnumSet<PlayerSkill> result = EnumSet.noneOf(PlayerSkill.class);
        positions.forEach(position -> result.addAll(POSITION_WEIGHTS.get(position).keySet()));
        return result;
    }

    private static Map<PlayerPosition, Map<PlayerSkill, Double>> positionWeights() {
        Map<PlayerPosition, Map<PlayerSkill, Double>> map = new EnumMap<>(PlayerPosition.class);
        map.put(PlayerPosition.GOALKEEPER, weights(
                w(PlayerSkill.GOALKEEPER_REFLEXES, 32),
                w(PlayerSkill.GOALKEEPER_POSITIONING, 30),
                w(PlayerSkill.GOALKEEPER_RUSHING_OUT, 23)));
        map.put(PlayerPosition.RIGHT_DEFENDER, weights(
                w(PlayerSkill.DEFENSIVE_POSITIONING, 20), w(PlayerSkill.TACKLING, 20),
                w(PlayerSkill.STRENGTH, 14), w(PlayerSkill.SPEED, 10),
                w(PlayerSkill.PASSING, 10), w(PlayerSkill.LONG_PASSING, 11)));
        map.put(PlayerPosition.LEFT_DEFENDER, map.get(PlayerPosition.RIGHT_DEFENDER));
        map.put(PlayerPosition.CENTER_DEFENDER, weights(
                w(PlayerSkill.DEFENSIVE_POSITIONING, 22), w(PlayerSkill.TACKLING, 19),
                w(PlayerSkill.STRENGTH, 14), w(PlayerSkill.HEADING, 12),
                w(PlayerSkill.PASSING, 8), w(PlayerSkill.LONG_PASSING, 10)));
        map.put(PlayerPosition.RIGHT_BACK, weights(
                w(PlayerSkill.SPEED, 15), w(PlayerSkill.AGILITY, 10),
                w(PlayerSkill.TACKLING, 15), w(PlayerSkill.DEFENSIVE_POSITIONING, 17),
                w(PlayerSkill.CROSSING, 15), w(PlayerSkill.PASSING, 13)));
        map.put(PlayerPosition.LEFT_BACK, map.get(PlayerPosition.RIGHT_BACK));
        map.put(PlayerPosition.DEFENSIVE_MIDFIELDER, weights(
                w(PlayerSkill.DEFENSIVE_POSITIONING, 16), w(PlayerSkill.TACKLING, 14),
                w(PlayerSkill.PASSING, 13), w(PlayerSkill.BALL_CONTROL, 10),
                w(PlayerSkill.VISION, 10), w(PlayerSkill.STRENGTH, 7),
                w(PlayerSkill.LONG_PASSING, 10), w(PlayerSkill.DRIBBLING, 5)));
        map.put(PlayerPosition.RIGHT_MIDFIELDER, weights(
                w(PlayerSkill.PASSING, 14), w(PlayerSkill.BALL_CONTROL, 13),
                w(PlayerSkill.VISION, 10), w(PlayerSkill.SPEED, 10),
                w(PlayerSkill.AGILITY, 8), w(PlayerSkill.CROSSING, 14),
                w(PlayerSkill.DRIBBLING, 10), w(PlayerSkill.FINISHING, 6)));
        map.put(PlayerPosition.LEFT_MIDFIELDER, map.get(PlayerPosition.RIGHT_MIDFIELDER));
        map.put(PlayerPosition.CENTRAL_MIDFIELDER, weights(
                w(PlayerSkill.PASSING, 14), w(PlayerSkill.BALL_CONTROL, 12),
                w(PlayerSkill.VISION, 13), w(PlayerSkill.AGILITY, 6),
                w(PlayerSkill.DEFENSIVE_POSITIONING, 8),
                w(PlayerSkill.ATTACKING_POSITIONING, 8),
                w(PlayerSkill.LONG_PASSING, 8), w(PlayerSkill.DRIBBLING, 7),
                w(PlayerSkill.FINISHING, 5), w(PlayerSkill.CROSSING, 4)));
        map.put(PlayerPosition.PLAYMAKER, weights(
                w(PlayerSkill.VISION, 16), w(PlayerSkill.PASSING, 15),
                w(PlayerSkill.BALL_CONTROL, 13), w(PlayerSkill.DRIBBLING, 11),
                w(PlayerSkill.ATTACKING_POSITIONING, 10),
                w(PlayerSkill.LONG_PASSING, 10), w(PlayerSkill.FINISHING, 6),
                w(PlayerSkill.CROSSING, 4)));
        map.put(PlayerPosition.RIGHT_WINGER, weights(
                w(PlayerSkill.SPEED, 16), w(PlayerSkill.AGILITY, 10),
                w(PlayerSkill.DRIBBLING, 17), w(PlayerSkill.CROSSING, 14),
                w(PlayerSkill.FINISHING, 13), w(PlayerSkill.ATTACKING_POSITIONING, 15)));
        map.put(PlayerPosition.LEFT_WINGER, map.get(PlayerPosition.RIGHT_WINGER));
        map.put(PlayerPosition.CENTER_FORWARD, weights(
                w(PlayerSkill.FINISHING, 22), w(PlayerSkill.ATTACKING_POSITIONING, 18),
                w(PlayerSkill.BALL_CONTROL, 13), w(PlayerSkill.STRENGTH, 10),
                w(PlayerSkill.HEADING, 10), w(PlayerSkill.DRIBBLING, 12)));
        return Map.copyOf(map);
    }

    private static Map<FutsalRole, Map<PlayerSkill, Double>> futsalWeights() {
        Map<FutsalRole, Map<PlayerSkill, Double>> map = new EnumMap<>(FutsalRole.class);
        map.put(FutsalRole.GOALKEEPER, weights(
                w(PlayerSkill.GOALKEEPER_REFLEXES, 22),
                w(PlayerSkill.GOALKEEPER_POSITIONING, 18),
                w(PlayerSkill.GOALKEEPER_RUSHING_OUT, 13),
                w(PlayerSkill.PASSING, 13), w(PlayerSkill.LONG_PASSING, 8),
                w(PlayerSkill.AGILITY, 11)));
        map.put(FutsalRole.FIXO, weights(
                w(PlayerSkill.DEFENSIVE_POSITIONING, 17), w(PlayerSkill.TACKLING, 15),
                w(PlayerSkill.PASSING, 13), w(PlayerSkill.BALL_CONTROL, 12),
                w(PlayerSkill.VISION, 9), w(PlayerSkill.STRENGTH, 9),
                w(PlayerSkill.AGILITY, 10)));
        Map<PlayerSkill, Double> winger = weights(
                w(PlayerSkill.SPEED, 13), w(PlayerSkill.AGILITY, 11),
                w(PlayerSkill.BALL_CONTROL, 13), w(PlayerSkill.DRIBBLING, 15),
                w(PlayerSkill.PASSING, 11), w(PlayerSkill.FINISHING, 10),
                w(PlayerSkill.ATTACKING_POSITIONING, 12));
        map.put(FutsalRole.RIGHT_WINGER_FUTSAL, winger);
        map.put(FutsalRole.LEFT_WINGER_FUTSAL, winger);
        map.put(FutsalRole.PIVOT, weights(
                w(PlayerSkill.FINISHING, 18), w(PlayerSkill.STRENGTH, 13),
                w(PlayerSkill.BALL_CONTROL, 15),
                w(PlayerSkill.ATTACKING_POSITIONING, 15),
                w(PlayerSkill.PASSING, 9), w(PlayerSkill.DRIBBLING, 8),
                w(PlayerSkill.VISION, 7)));
        return Map.copyOf(map);
    }

    private static Map<PlayerSkill, Double> weights(SkillWeight... weights) {
        Map<PlayerSkill, Double> result = new EnumMap<>(PlayerSkill.class);
        double total = 0;
        for (SkillWeight weight : weights) {
            result.put(weight.skill(), weight.value());
            total += weight.value();
        }
        if (Math.abs(total - CHARACTERISTIC_WEIGHT) > 0.0001) {
            throw new IllegalStateException("Characteristic skill weights must total 85");
        }
        return Map.copyOf(result);
    }

    private static SkillWeight w(PlayerSkill skill, double value) {
        return new SkillWeight(skill, value);
    }

    private record SkillWeight(PlayerSkill skill, double value) {
    }

    public record OverallResult(
            Integer overall,
            int coverage,
            boolean reliable,
            boolean estimated,
            PlayerPosition resolvedPosition,
            List<PlayerSkill> missingEssentialSkills) {
    }
}
