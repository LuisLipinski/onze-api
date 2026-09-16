package com.onze.api.technical;

import java.util.Map;
import java.util.Set;

import com.onze.api.group.PlayerPosition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalRatingPolicyTest {

    private static final Set<PlayerPosition> SPECIALIZED_POSITIONS = Set.of(
            PlayerPosition.GOALKEEPER,
            PlayerPosition.RIGHT_DEFENDER,
            PlayerPosition.LEFT_DEFENDER,
            PlayerPosition.CENTER_DEFENDER,
            PlayerPosition.RIGHT_BACK,
            PlayerPosition.LEFT_BACK,
            PlayerPosition.DEFENSIVE_MIDFIELDER,
            PlayerPosition.RIGHT_MIDFIELDER,
            PlayerPosition.LEFT_MIDFIELDER,
            PlayerPosition.CENTRAL_MIDFIELDER,
            PlayerPosition.PLAYMAKER,
            PlayerPosition.RIGHT_WINGER,
            PlayerPosition.LEFT_WINGER,
            PlayerPosition.CENTER_FORWARD);

    @Test
    void shouldKeepMissingSkillsOutOfGeneralOverall() {
        var result = TechnicalRatingPolicy.general(Map.of(
                PlayerSkill.PASSING, 10,
                PlayerSkill.FINISHING, 2));

        assertThat(result.overall()).isEqualTo(30);
        assertThat(result.coverage()).isEqualTo(14);
        assertThat(result.reliable()).isFalse();
    }

    @Test
    void shouldExcludeGoalkeeperSkillsFromGeneralOverall() {
        var result = TechnicalRatingPolicy.general(Map.of(
                PlayerSkill.PASSING, 8,
                PlayerSkill.GOALKEEPER_REFLEXES, 1,
                PlayerSkill.GOALKEEPER_POSITIONING, 1,
                PlayerSkill.GOALKEEPER_RUSHING_OUT, 1));

        assertThat(result.overall()).isEqualTo(40);
        assertThat(result.coverage()).isEqualTo(7);
    }

    @Test
    void shouldRequireAllEssentialSkillsForReliablePositionOverall() {
        var incomplete = TechnicalRatingPolicy.position(
                Map.of(PlayerSkill.FINISHING, 10), PlayerPosition.CENTER_FORWARD);
        assertThat(incomplete.overall()).isEqualTo(50);
        assertThat(incomplete.reliable()).isFalse();
        assertThat(incomplete.missingEssentialSkills()).isNotEmpty();

        var completeRatings = new java.util.EnumMap<PlayerSkill, Integer>(PlayerSkill.class);
        TechnicalRatingPolicy.essentialSkills(PlayerPosition.CENTER_FORWARD)
                .forEach(skill -> completeRatings.put(skill, 8));
        var complete = TechnicalRatingPolicy.position(completeRatings, PlayerPosition.CENTER_FORWARD);
        assertThat(complete.overall()).isEqualTo(40);
        assertThat(complete.reliable()).isTrue();
        assertThat(complete.coverage()).isEqualTo(85);
    }

    @Test
    void shouldUseBestSpecializationForGenericPositions() {
        var ratings = new java.util.EnumMap<PlayerSkill, Integer>(PlayerSkill.class);
        TechnicalRatingPolicy.essentialSkills(PlayerPosition.CENTER_FORWARD)
                .forEach(skill -> ratings.put(skill, 10));

        var result = TechnicalRatingPolicy.position(ratings, PlayerPosition.ATTACKER);

        assertThat(result.resolvedPosition()).isEqualTo(PlayerPosition.CENTER_FORWARD);
        assertThat(result.overall()).isEqualTo(50);
    }

    @Test
    void shouldCalculateFutsalRolesFromSameSkills() {
        var ratings = new java.util.EnumMap<PlayerSkill, Integer>(PlayerSkill.class);
        TechnicalRatingPolicy.essentialSkills(FutsalRole.FIXO)
                .forEach(skill -> ratings.put(skill, 6));

        var result = TechnicalRatingPolicy.futsal(ratings, FutsalRole.FIXO);

        assertThat(result.overall()).isEqualTo(30);
        assertThat(result.reliable()).isTrue();
    }

    @Test
    void shouldIncludeLongPassingWithRelevantWeightForDefenders() {
        assertThat(TechnicalRatingPolicy.essentialSkills(PlayerPosition.DEFENDER))
                .contains(PlayerSkill.LONG_PASSING);
        assertThat(TechnicalRatingPolicy.essentialSkills(PlayerPosition.CENTER_DEFENDER))
                .contains(PlayerSkill.LONG_PASSING);

        var lowLongPassing = ratingsFor(PlayerPosition.CENTER_DEFENDER, 2);
        var highLongPassing = ratingsFor(PlayerPosition.CENTER_DEFENDER, 2);
        highLongPassing.put(PlayerSkill.LONG_PASSING, 10);

        assertThat(TechnicalRatingPolicy.position(
                highLongPassing, PlayerPosition.CENTER_DEFENDER).overall())
                .isGreaterThan(TechnicalRatingPolicy.position(
                        lowLongPassing, PlayerPosition.CENTER_DEFENDER).overall());
    }

    @Test
    void shouldIncludeFinishingCrossingAndDribblingForMidfieldRoles() {
        assertThat(TechnicalRatingPolicy.essentialSkills(PlayerPosition.MIDFIELDER))
                .contains(
                        PlayerSkill.FINISHING,
                        PlayerSkill.CROSSING,
                        PlayerSkill.DRIBBLING);
        assertThat(TechnicalRatingPolicy.essentialSkills(PlayerPosition.RIGHT_MIDFIELDER))
                .contains(
                        PlayerSkill.FINISHING,
                        PlayerSkill.CROSSING,
                        PlayerSkill.DRIBBLING);
        assertThat(TechnicalRatingPolicy.essentialSkills(PlayerPosition.CENTRAL_MIDFIELDER))
                .contains(
                        PlayerSkill.FINISHING,
                        PlayerSkill.CROSSING,
                        PlayerSkill.DRIBBLING);
    }

    @Test
    void shouldKeepCharacteristicCoverageAtEightyFivePercentForEveryPosition() {
        for (PlayerPosition position : SPECIALIZED_POSITIONS) {
            var result = TechnicalRatingPolicy.position(ratingsFor(position, 8), position);

            assertThat(result.overall()).as(position.name()).isEqualTo(40);
            assertThat(result.coverage()).as(position.name()).isEqualTo(85);
            assertThat(result.reliable()).as(position.name()).isTrue();
            assertThat(result.overall()).as(position.name()).isBetween(0, 50);
        }
    }

    @Test
    void shouldKeepCharacteristicCoverageAtEightyFivePercentForEveryFutsalRole() {
        for (FutsalRole role : FutsalRole.values()) {
            var ratings = new java.util.EnumMap<PlayerSkill, Integer>(PlayerSkill.class);
            TechnicalRatingPolicy.essentialSkills(role).forEach(skill -> ratings.put(skill, 8));

            var result = TechnicalRatingPolicy.futsal(ratings, role);

            assertThat(result.overall()).as(role.name()).isEqualTo(40);
            assertThat(result.coverage()).as(role.name()).isEqualTo(85);
            assertThat(result.reliable()).as(role.name()).isTrue();
        }
    }

    @Test
    void shouldApplyDifferentWeightsAndKeepNullSkillsOutOfTheScore() {
        var reflexOnly = TechnicalRatingPolicy.position(
                Map.of(PlayerSkill.GOALKEEPER_REFLEXES, 10),
                PlayerPosition.GOALKEEPER);
        var rushingOnly = TechnicalRatingPolicy.position(
                Map.of(PlayerSkill.GOALKEEPER_RUSHING_OUT, 10),
                PlayerPosition.GOALKEEPER);
        var noRatings = TechnicalRatingPolicy.position(Map.of(), PlayerPosition.GOALKEEPER);

        assertThat(reflexOnly.coverage()).isGreaterThan(rushingOnly.coverage());
        assertThat(noRatings.overall()).isNull();
        assertThat(noRatings.coverage()).isZero();
    }

    private java.util.EnumMap<PlayerSkill, Integer> ratingsFor(
            PlayerPosition position,
            int rating) {
        var ratings = new java.util.EnumMap<PlayerSkill, Integer>(PlayerSkill.class);
        TechnicalRatingPolicy.essentialSkills(position)
                .forEach(skill -> ratings.put(skill, rating));
        return ratings;
    }
}
