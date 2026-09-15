package com.onze.api.technical;

import java.util.Map;

import com.onze.api.group.PlayerPosition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalRatingPolicyTest {

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
}
