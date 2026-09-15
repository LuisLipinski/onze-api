package com.onze.api.match;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchPlayerPolicyTest {

    @Test
    void shouldCalculateIdealsForEveryModalityAndTeamCount() {
        assertThat(MatchPlayerPolicy.resolve(
                MatchModality.FIELD, 10, 30, MatchType.INTERNAL, 2).idealPlayers())
                .isEqualTo(22);
        assertThat(MatchPlayerPolicy.resolve(
                MatchModality.FUT7, 10, 30, MatchType.INTERNAL, 3).idealPlayers())
                .isEqualTo(21);
        assertThat(MatchPlayerPolicy.resolve(
                MatchModality.FUTSAL, 8, 20, MatchType.INTERNAL, 2).idealPlayers())
                .isEqualTo(10);
    }

    @Test
    void shouldUseSafeLegacyDefaults() {
        var configuration = MatchPlayerPolicy.resolve(
                null, null, 12, MatchType.INTERNAL, 2);

        assertThat(configuration.modality()).isEqualTo(MatchModality.FUT7);
        assertThat(configuration.minimumPlayers()).isEqualTo(12);
        assertThat(configuration.idealPlayers()).isEqualTo(14);
    }

    @Test
    void shouldRejectMinimumOutsideCapacity() {
        assertThatThrownBy(() -> MatchPlayerPolicy.resolve(
                MatchModality.FUT7, 0, 14, MatchType.INTERNAL, 2))
                .isInstanceOf(MatchPlayerPolicy.InvalidMinimumPlayersException.class);
        assertThatThrownBy(() -> MatchPlayerPolicy.resolve(
                MatchModality.FUT7, 15, 14, MatchType.INTERNAL, 2))
                .isInstanceOf(MatchPlayerPolicy.InvalidMinimumPlayersException.class);
    }
}
