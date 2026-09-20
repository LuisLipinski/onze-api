package com.onze.api.devtools;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.onze.api.group.DominantFoot;
import com.onze.api.group.PlayerPosition;
import com.onze.api.match.MatchModality;
import com.onze.api.technical.PlayerSkill;

final class DevTestDataScenarioPolicy {

    private static final List<PlayerPosition> FUTSAL_TEMPLATE = List.of(
            PlayerPosition.GOALKEEPER,
            PlayerPosition.DEFENDER,
            PlayerPosition.MIDFIELDER,
            PlayerPosition.MIDFIELDER,
            PlayerPosition.ATTACKER);

    private static final List<PlayerPosition> FUT7_TEMPLATE = List.of(
            PlayerPosition.GOALKEEPER,
            PlayerPosition.RIGHT_DEFENDER,
            PlayerPosition.LEFT_DEFENDER,
            PlayerPosition.RIGHT_MIDFIELDER,
            PlayerPosition.CENTRAL_MIDFIELDER,
            PlayerPosition.LEFT_MIDFIELDER,
            PlayerPosition.CENTER_FORWARD);

    private static final List<PlayerPosition> FIELD_TEMPLATE = List.of(
            PlayerPosition.GOALKEEPER,
            PlayerPosition.RIGHT_BACK,
            PlayerPosition.CENTER_DEFENDER,
            PlayerPosition.DEFENDER,
            PlayerPosition.LEFT_BACK,
            PlayerPosition.DEFENSIVE_MIDFIELDER,
            PlayerPosition.CENTRAL_MIDFIELDER,
            PlayerPosition.PLAYMAKER,
            PlayerPosition.RIGHT_WINGER,
            PlayerPosition.CENTER_FORWARD,
            PlayerPosition.LEFT_WINGER);

    private DevTestDataScenarioPolicy() {
    }

    static TestProfile profile(
            int playerNumber,
            int idealPlayers,
            MatchModality modality,
            DevTestDataScenario scenario) {
        if (playerNumber < 1 || idealPlayers < 1) {
            throw new IllegalArgumentException("Player number and ideal players must be positive");
        }

        PlayerPosition primary = basePosition(playerNumber, modality);
        PlayerPosition secondary = null;
        boolean canPlayGoalkeeper = false;

        if (scenario == DevTestDataScenario.SECONDARY_POSITIONS) {
            secondary = secondaryFor(primary, playerNumber);
        } else if (scenario == DevTestDataScenario.GOALKEEPER_PRIORITY) {
            if (playerNumber == 1) {
                primary = PlayerPosition.GOALKEEPER;
            } else if (playerNumber == 2) {
                primary = PlayerPosition.DEFENDER;
                secondary = PlayerPosition.GOALKEEPER;
            } else if (playerNumber == 3) {
                primary = PlayerPosition.DEFENDER;
                canPlayGoalkeeper = true;
            } else if (playerNumber == 4) {
                primary = PlayerPosition.MIDFIELDER;
                canPlayGoalkeeper = true;
            }
        }

        if (primary == PlayerPosition.GOALKEEPER || secondary == PlayerPosition.GOALKEEPER) {
            canPlayGoalkeeper = false;
        }

        Map<PlayerSkill, Integer> ratings = ratings(primary, playerNumber, idealPlayers, scenario);
        if (scenario == DevTestDataScenario.GOALKEEPER_PRIORITY) {
            if (playerNumber == 2) {
                setGoalkeeperSkills(ratings, 8);
            } else if (playerNumber == 3 || playerNumber == 4) {
                setGoalkeeperSkills(ratings, 6);
            }
        }

        DominantFoot dominantFoot = switch (playerNumber % 3) {
            case 1 -> DominantFoot.RIGHT;
            case 2 -> DominantFoot.LEFT;
            default -> DominantFoot.BOTH;
        };

        return new TestProfile(primary, secondary, canPlayGoalkeeper, dominantFoot, Map.copyOf(ratings));
    }

    private static PlayerPosition basePosition(int playerNumber, MatchModality modality) {
        List<PlayerPosition> template = switch (modality) {
            case FUTSAL -> FUTSAL_TEMPLATE;
            case FUT7 -> FUT7_TEMPLATE;
            case FIELD -> FIELD_TEMPLATE;
        };
        return template.get((playerNumber - 1) % template.size());
    }

    private static PlayerPosition secondaryFor(PlayerPosition primary, int playerNumber) {
        if (primary == PlayerPosition.GOALKEEPER) {
            return null;
        }
        if (isDefense(primary)) {
            return playerNumber % 2 == 0
                    ? PlayerPosition.DEFENSIVE_MIDFIELDER
                    : PlayerPosition.MIDFIELDER;
        }
        if (isMidfield(primary)) {
            return playerNumber % 2 == 0
                    ? PlayerPosition.ATTACKER
                    : PlayerPosition.DEFENDER;
        }
        return PlayerPosition.MIDFIELDER;
    }

    private static Map<PlayerSkill, Integer> ratings(
            PlayerPosition primary,
            int playerNumber,
            int idealPlayers,
            DevTestDataScenario scenario) {
        EnumMap<PlayerSkill, Integer> ratings = new EnumMap<>(PlayerSkill.class);
        int base = switch (scenario) {
            case UNEVEN -> unevenBase(playerNumber, idealPlayers);
            case SPECIALISTS -> 3;
            default -> realisticBase(playerNumber);
        };
        for (PlayerSkill skill : PlayerSkill.values()) {
            ratings.put(skill, clamp(base));
        }

        switch (scenario) {
            case ATTACK_VS_DEFENSE -> applyAttackVsDefense(ratings, playerNumber);
            case SPECIALISTS -> applySpecialist(ratings, primary);
            case UNEVEN -> applyRoleBoosts(ratings, primary, clamp(base + 1));
            default -> applyRoleBoosts(ratings, primary, clamp(base + 2));
        }
        return ratings;
    }

    private static int realisticBase(int playerNumber) {
        int[] distribution = {3, 5, 7, 4, 8, 2, 6, 9, 5, 7, 3, 6, 8, 4};
        return distribution[(playerNumber - 1) % distribution.length];
    }

    private static int unevenBase(int playerNumber, int idealPlayers) {
        double ratio = (double) playerNumber / Math.max(idealPlayers, 1);
        if (ratio <= 0.34) {
            return 8;
        }
        if (ratio <= 0.67) {
            return 6;
        }
        return 3;
    }

    private static void applyAttackVsDefense(Map<PlayerSkill, Integer> ratings, int playerNumber) {
        boolean attackHeavy = playerNumber % 2 == 1;
        setSkills(ratings, attackHeavy ? 9 : 4,
                PlayerSkill.FINISHING,
                PlayerSkill.ATTACKING_POSITIONING,
                PlayerSkill.SPEED,
                PlayerSkill.DRIBBLING,
                PlayerSkill.BALL_CONTROL,
                PlayerSkill.CROSSING);
        setSkills(ratings, attackHeavy ? 4 : 9,
                PlayerSkill.TACKLING,
                PlayerSkill.DEFENSIVE_POSITIONING,
                PlayerSkill.STRENGTH,
                PlayerSkill.HEADING,
                PlayerSkill.LONG_PASSING);
        setSkills(ratings, 6,
                PlayerSkill.PASSING,
                PlayerSkill.VISION,
                PlayerSkill.AGILITY);
        if (playerNumber % 5 == 1) {
            setGoalkeeperSkills(ratings, 8);
        }
    }

    private static void applySpecialist(Map<PlayerSkill, Integer> ratings, PlayerPosition primary) {
        applyRoleBoosts(ratings, primary, 9);
        if (primary == PlayerPosition.GOALKEEPER) {
            setSkills(ratings, 5, PlayerSkill.PASSING, PlayerSkill.LONG_PASSING);
        } else if (isDefense(primary)) {
            setSkills(ratings, 5, PlayerSkill.PASSING, PlayerSkill.SPEED);
        } else if (isMidfield(primary)) {
            setSkills(ratings, 5, PlayerSkill.FINISHING, PlayerSkill.CROSSING);
        } else {
            setSkills(ratings, 5, PlayerSkill.PASSING, PlayerSkill.HEADING);
        }
    }

    private static void applyRoleBoosts(
            Map<PlayerSkill, Integer> ratings,
            PlayerPosition primary,
            int coreValue) {
        if (primary == PlayerPosition.GOALKEEPER) {
            setGoalkeeperSkills(ratings, coreValue);
            setSkills(ratings, clamp(coreValue - 1),
                    PlayerSkill.AGILITY,
                    PlayerSkill.PASSING,
                    PlayerSkill.LONG_PASSING);
            return;
        }
        if (isDefense(primary)) {
            setSkills(ratings, coreValue,
                    PlayerSkill.TACKLING,
                    PlayerSkill.DEFENSIVE_POSITIONING,
                    PlayerSkill.STRENGTH,
                    PlayerSkill.HEADING,
                    PlayerSkill.LONG_PASSING);
            setSkills(ratings, clamp(coreValue - 1),
                    PlayerSkill.PASSING,
                    PlayerSkill.SPEED,
                    PlayerSkill.BALL_CONTROL);
            return;
        }
        if (isMidfield(primary)) {
            setSkills(ratings, coreValue,
                    PlayerSkill.PASSING,
                    PlayerSkill.VISION,
                    PlayerSkill.BALL_CONTROL,
                    PlayerSkill.DRIBBLING);
            setSkills(ratings, clamp(coreValue - 1),
                    PlayerSkill.CROSSING,
                    PlayerSkill.FINISHING,
                    PlayerSkill.AGILITY,
                    PlayerSkill.LONG_PASSING);
            return;
        }
        setSkills(ratings, coreValue,
                PlayerSkill.FINISHING,
                PlayerSkill.ATTACKING_POSITIONING,
                PlayerSkill.SPEED,
                PlayerSkill.DRIBBLING,
                PlayerSkill.BALL_CONTROL);
        setSkills(ratings, clamp(coreValue - 1),
                PlayerSkill.HEADING,
                PlayerSkill.CROSSING,
                PlayerSkill.AGILITY);
    }

    private static void setGoalkeeperSkills(Map<PlayerSkill, Integer> ratings, int value) {
        setSkills(ratings, value,
                PlayerSkill.GOALKEEPER_REFLEXES,
                PlayerSkill.GOALKEEPER_POSITIONING,
                PlayerSkill.GOALKEEPER_RUSHING_OUT);
    }

    private static void setSkills(
            Map<PlayerSkill, Integer> ratings,
            int value,
            PlayerSkill... skills) {
        int normalized = clamp(value);
        for (PlayerSkill skill : skills) {
            ratings.put(skill, normalized);
        }
    }

    private static boolean isDefense(PlayerPosition position) {
        return switch (position) {
            case DEFENDER, RIGHT_DEFENDER, LEFT_DEFENDER, CENTER_DEFENDER, RIGHT_BACK, LEFT_BACK -> true;
            default -> false;
        };
    }

    private static boolean isMidfield(PlayerPosition position) {
        return switch (position) {
            case DEFENSIVE_MIDFIELDER, MIDFIELDER, RIGHT_MIDFIELDER, LEFT_MIDFIELDER,
                    CENTRAL_MIDFIELDER, PLAYMAKER -> true;
            default -> false;
        };
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(10, value));
    }

    record TestProfile(
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            boolean canPlayGoalkeeper,
            DominantFoot dominantFoot,
            Map<PlayerSkill, Integer> ratings) {
    }
}
