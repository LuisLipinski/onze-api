package com.onze.api.devtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onze.api.group.PlayerPosition;
import com.onze.api.match.MatchModality;
import com.onze.api.technical.PlayerSkill;

import org.junit.jupiter.api.Test;

class DevTestDataScenarioPolicyTest {

    @Test
    void balancedPresetProvidesEnoughGoalkeepersForTwoFutsalTeams() {
        assertEquals(
                PlayerPosition.GOALKEEPER,
                DevTestDataScenarioPolicy.profile(
                        1, 10, MatchModality.FUTSAL, DevTestDataScenario.BALANCED).primaryPosition());
        assertEquals(
                PlayerPosition.GOALKEEPER,
                DevTestDataScenarioPolicy.profile(
                        6, 10, MatchModality.FUTSAL, DevTestDataScenario.BALANCED).primaryPosition());
    }

    @Test
    void balancedPresetProvidesEnoughGoalkeepersForTwoFut7Teams() {
        assertEquals(
                PlayerPosition.GOALKEEPER,
                DevTestDataScenarioPolicy.profile(
                        1, 14, MatchModality.FUT7, DevTestDataScenario.BALANCED).primaryPosition());
        assertEquals(
                PlayerPosition.GOALKEEPER,
                DevTestDataScenarioPolicy.profile(
                        8, 14, MatchModality.FUT7, DevTestDataScenario.BALANCED).primaryPosition());
    }

    @Test
    void modalityControlsTemplateEvenWhenReusablePoolIsLargerThanIdeal() {
        assertEquals(
                PlayerPosition.GOALKEEPER,
                DevTestDataScenarioPolicy.profile(
                        15, 14, MatchModality.FUT7, DevTestDataScenario.BALANCED).primaryPosition());
        assertEquals(
                PlayerPosition.LEFT_DEFENDER,
                DevTestDataScenarioPolicy.profile(
                        17, 14, MatchModality.FUT7, DevTestDataScenario.BALANCED).primaryPosition());
        assertEquals(
                PlayerPosition.GOALKEEPER,
                DevTestDataScenarioPolicy.profile(
                        11, 10, MatchModality.FUTSAL, DevTestDataScenario.BALANCED).primaryPosition());
    }

    @Test
    void secondaryScenarioAddsFlexiblePositionsWithoutRepeatingPrimary() {
        for (int number = 1; number <= 14; number++) {
            var profile = DevTestDataScenarioPolicy.profile(
                    number, 14, MatchModality.FUT7, DevTestDataScenario.SECONDARY_POSITIONS);
            if (profile.primaryPosition() == PlayerPosition.GOALKEEPER) {
                assertNull(profile.secondaryPosition());
            } else {
                assertNotEquals(profile.primaryPosition(), profile.secondaryPosition());
            }
        }
    }

    @Test
    void goalkeeperScenarioRepresentsPrimarySecondaryAndEmergencyCandidates() {
        var primary = DevTestDataScenarioPolicy.profile(
                1, 14, MatchModality.FUT7, DevTestDataScenario.GOALKEEPER_PRIORITY);
        var secondary = DevTestDataScenarioPolicy.profile(
                2, 14, MatchModality.FUT7, DevTestDataScenario.GOALKEEPER_PRIORITY);
        var emergency = DevTestDataScenarioPolicy.profile(
                3, 14, MatchModality.FUT7, DevTestDataScenario.GOALKEEPER_PRIORITY);

        assertEquals(PlayerPosition.GOALKEEPER, primary.primaryPosition());
        assertFalse(primary.canPlayGoalkeeper());
        assertEquals(PlayerPosition.GOALKEEPER, secondary.secondaryPosition());
        assertFalse(secondary.canPlayGoalkeeper());
        assertTrue(emergency.canPlayGoalkeeper());
    }

    @Test
    void attackVsDefenseScenarioCreatesOpposingStrengthProfiles() {
        var attackHeavy = DevTestDataScenarioPolicy.profile(
                1, 14, MatchModality.FUT7, DevTestDataScenario.ATTACK_VS_DEFENSE);
        var defenseHeavy = DevTestDataScenarioPolicy.profile(
                2, 14, MatchModality.FUT7, DevTestDataScenario.ATTACK_VS_DEFENSE);

        assertTrue(attackHeavy.ratings().get(PlayerSkill.FINISHING)
                > attackHeavy.ratings().get(PlayerSkill.TACKLING));
        assertTrue(defenseHeavy.ratings().get(PlayerSkill.TACKLING)
                > defenseHeavy.ratings().get(PlayerSkill.FINISHING));
    }

    @Test
    void balancedScenarioSpansWeakAverageAndStrongPlayers() {
        var values = java.util.stream.IntStream.rangeClosed(1, 14)
                .mapToObj(number -> DevTestDataScenarioPolicy.profile(
                        number, 14, MatchModality.FUT7, DevTestDataScenario.BALANCED))
                .flatMap(profile -> profile.ratings().values().stream())
                .toList();

        assertTrue(values.stream().anyMatch(value -> value <= 3));
        assertTrue(values.stream().anyMatch(value -> value >= 9));
        assertTrue(values.stream().distinct().count() >= 7);
    }

    @Test
    void everyScenarioProducesAllSeventeenRatingsWithinSupportedRange() {
        for (DevTestDataScenario scenario : DevTestDataScenario.values()) {
            var profile = DevTestDataScenarioPolicy.profile(
                    5, 14, MatchModality.FUT7, scenario);
            assertEquals(PlayerSkill.values().length, profile.ratings().size());
            assertTrue(profile.ratings().values().stream()
                    .allMatch(rating -> rating >= 1 && rating <= 10));
        }
    }
}
