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

    private static final Map<PlayerPosition, Set<PlayerSkill>> POSITION_ESSENTIALS = positionEssentials();
    private static final Map<FutsalRole, Set<PlayerSkill>> FUTSAL_ESSENTIALS = futsalEssentials();

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
        Set<PlayerSkill> essential = POSITION_ESSENTIALS.get(position);
        Set<PlayerSkill> complements = position == PlayerPosition.GOALKEEPER
                ? GOALKEEPER_COMPLEMENTS
                : difference(LINE_SKILLS, essential);
        return weighted(ratings, essential, complements, position);
    }

    public static OverallResult futsal(Map<PlayerSkill, Integer> ratings, FutsalRole role) {
        Set<PlayerSkill> essential = FUTSAL_ESSENTIALS.get(role);
        Set<PlayerSkill> complements = role == FutsalRole.GOALKEEPER
                ? difference(GOALKEEPER_COMPLEMENTS, essential)
                : difference(LINE_SKILLS, essential);
        return weighted(ratings, essential, complements, null);
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
        return Set.copyOf(POSITION_ESSENTIALS.get(position));
    }

    public static Set<PlayerSkill> essentialSkills(FutsalRole role) {
        return Set.copyOf(FUTSAL_ESSENTIALS.get(role));
    }

    private static OverallResult bestPosition(
            Map<PlayerSkill, Integer> ratings,
            List<PlayerPosition> specializations) {
        return specializations.stream()
                .map(position -> {
                    Set<PlayerSkill> essential = POSITION_ESSENTIALS.get(position);
                    return weighted(ratings, essential, difference(LINE_SKILLS, essential), position);
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
            Set<PlayerSkill> essential,
            Set<PlayerSkill> complements,
            PlayerPosition resolvedPosition) {
        double essentialWeight = essential.isEmpty() ? 0 : 85.0 / essential.size();
        double complementWeight = complements.isEmpty() ? 0 : 15.0 / complements.size();
        double knownWeight = 0;
        double weightedScore = 0;
        List<PlayerSkill> missing = new ArrayList<>();

        for (PlayerSkill skill : essential) {
            Integer rating = ratings.get(skill);
            if (rating == null) {
                missing.add(skill);
            } else {
                knownWeight += essentialWeight;
                weightedScore += rating * 5.0 * essentialWeight;
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
        boolean reliable = missing.isEmpty() && !essential.isEmpty();
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
        positions.forEach(position -> result.addAll(POSITION_ESSENTIALS.get(position)));
        return result;
    }

    private static Map<PlayerPosition, Set<PlayerSkill>> positionEssentials() {
        Map<PlayerPosition, Set<PlayerSkill>> map = new EnumMap<>(PlayerPosition.class);
        map.put(PlayerPosition.GOALKEEPER, skills(
                PlayerSkill.GOALKEEPER_REFLEXES, PlayerSkill.GOALKEEPER_POSITIONING,
                PlayerSkill.GOALKEEPER_RUSHING_OUT));
        map.put(PlayerPosition.RIGHT_DEFENDER, skills(
                PlayerSkill.DEFENSIVE_POSITIONING, PlayerSkill.TACKLING, PlayerSkill.STRENGTH,
                PlayerSkill.SPEED, PlayerSkill.PASSING));
        map.put(PlayerPosition.LEFT_DEFENDER, map.get(PlayerPosition.RIGHT_DEFENDER));
        map.put(PlayerPosition.CENTER_DEFENDER, skills(
                PlayerSkill.DEFENSIVE_POSITIONING, PlayerSkill.TACKLING, PlayerSkill.STRENGTH,
                PlayerSkill.HEADING, PlayerSkill.PASSING));
        map.put(PlayerPosition.RIGHT_BACK, skills(
                PlayerSkill.SPEED, PlayerSkill.AGILITY, PlayerSkill.TACKLING,
                PlayerSkill.DEFENSIVE_POSITIONING, PlayerSkill.CROSSING, PlayerSkill.PASSING));
        map.put(PlayerPosition.LEFT_BACK, map.get(PlayerPosition.RIGHT_BACK));
        map.put(PlayerPosition.DEFENSIVE_MIDFIELDER, skills(
                PlayerSkill.DEFENSIVE_POSITIONING, PlayerSkill.TACKLING, PlayerSkill.PASSING,
                PlayerSkill.BALL_CONTROL, PlayerSkill.VISION, PlayerSkill.STRENGTH));
        map.put(PlayerPosition.RIGHT_MIDFIELDER, skills(
                PlayerSkill.PASSING, PlayerSkill.BALL_CONTROL, PlayerSkill.VISION,
                PlayerSkill.SPEED, PlayerSkill.AGILITY, PlayerSkill.CROSSING));
        map.put(PlayerPosition.LEFT_MIDFIELDER, map.get(PlayerPosition.RIGHT_MIDFIELDER));
        map.put(PlayerPosition.CENTRAL_MIDFIELDER, skills(
                PlayerSkill.PASSING, PlayerSkill.BALL_CONTROL, PlayerSkill.VISION,
                PlayerSkill.AGILITY, PlayerSkill.DEFENSIVE_POSITIONING,
                PlayerSkill.ATTACKING_POSITIONING));
        map.put(PlayerPosition.PLAYMAKER, skills(
                PlayerSkill.VISION, PlayerSkill.PASSING, PlayerSkill.BALL_CONTROL,
                PlayerSkill.DRIBBLING, PlayerSkill.ATTACKING_POSITIONING,
                PlayerSkill.LONG_PASSING));
        map.put(PlayerPosition.RIGHT_WINGER, skills(
                PlayerSkill.SPEED, PlayerSkill.AGILITY, PlayerSkill.DRIBBLING,
                PlayerSkill.CROSSING, PlayerSkill.FINISHING,
                PlayerSkill.ATTACKING_POSITIONING));
        map.put(PlayerPosition.LEFT_WINGER, map.get(PlayerPosition.RIGHT_WINGER));
        map.put(PlayerPosition.CENTER_FORWARD, skills(
                PlayerSkill.FINISHING, PlayerSkill.ATTACKING_POSITIONING,
                PlayerSkill.BALL_CONTROL, PlayerSkill.STRENGTH,
                PlayerSkill.HEADING, PlayerSkill.DRIBBLING));
        return Map.copyOf(map);
    }

    private static Map<FutsalRole, Set<PlayerSkill>> futsalEssentials() {
        Map<FutsalRole, Set<PlayerSkill>> map = new EnumMap<>(FutsalRole.class);
        map.put(FutsalRole.GOALKEEPER, skills(
                PlayerSkill.GOALKEEPER_REFLEXES, PlayerSkill.GOALKEEPER_POSITIONING,
                PlayerSkill.GOALKEEPER_RUSHING_OUT, PlayerSkill.PASSING,
                PlayerSkill.LONG_PASSING, PlayerSkill.AGILITY));
        map.put(FutsalRole.FIXO, skills(
                PlayerSkill.DEFENSIVE_POSITIONING, PlayerSkill.TACKLING, PlayerSkill.PASSING,
                PlayerSkill.BALL_CONTROL, PlayerSkill.VISION, PlayerSkill.STRENGTH,
                PlayerSkill.AGILITY));
        Set<PlayerSkill> winger = skills(
                PlayerSkill.SPEED, PlayerSkill.AGILITY, PlayerSkill.BALL_CONTROL,
                PlayerSkill.DRIBBLING, PlayerSkill.PASSING, PlayerSkill.FINISHING,
                PlayerSkill.ATTACKING_POSITIONING);
        map.put(FutsalRole.RIGHT_WINGER_FUTSAL, winger);
        map.put(FutsalRole.LEFT_WINGER_FUTSAL, winger);
        map.put(FutsalRole.PIVOT, skills(
                PlayerSkill.FINISHING, PlayerSkill.STRENGTH, PlayerSkill.BALL_CONTROL,
                PlayerSkill.ATTACKING_POSITIONING, PlayerSkill.PASSING,
                PlayerSkill.DRIBBLING, PlayerSkill.VISION));
        return Map.copyOf(map);
    }

    private static Set<PlayerSkill> skills(PlayerSkill... skills) {
        return Set.copyOf(List.of(skills));
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
