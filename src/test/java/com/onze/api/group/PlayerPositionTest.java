package com.onze.api.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerPositionTest {

    @Test
    void shouldExpandOnlyGenericDefenderPositions() {
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.DEFENDER,
                PlayerPosition.RIGHT_DEFENDER)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.DEFENDER,
                PlayerPosition.LEFT_BACK)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.RIGHT_DEFENDER,
                PlayerPosition.CENTER_DEFENDER)).isFalse();
    }

    @Test
    void shouldExpandOnlyGenericMidfielderPositions() {
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.MIDFIELDER,
                PlayerPosition.DEFENSIVE_MIDFIELDER)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.MIDFIELDER,
                PlayerPosition.PLAYMAKER)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.RIGHT_MIDFIELDER,
                PlayerPosition.LEFT_MIDFIELDER)).isFalse();
    }

    @Test
    void shouldExpandOnlyGenericAttackerPositions() {
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.ATTACKER,
                PlayerPosition.RIGHT_WINGER)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.ATTACKER,
                PlayerPosition.CENTER_FORWARD)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.RIGHT_WINGER,
                PlayerPosition.CENTER_FORWARD)).isFalse();
    }

    @Test
    void shouldMatchGoalkeeperAndSpecializedPositionsOnlyToThemselves() {
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.GOALKEEPER,
                PlayerPosition.GOALKEEPER)).isTrue();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.GOALKEEPER,
                PlayerPosition.DEFENDER)).isFalse();
        assertThat(PlayerPosition.canPlayPosition(
                PlayerPosition.CENTER_FORWARD,
                PlayerPosition.CENTER_FORWARD)).isTrue();
    }
}
