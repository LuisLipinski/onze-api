package com.onze.api.match;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchFormatPolicyTest {

    @Test
    void shouldKeepLegacyRequestsCompatibleWithTwoInternalTeams() {
        MatchFormatPolicy.MatchFormat format = MatchFormatPolicy.resolve(null, null, null);

        assertThat(format.matchType()).isEqualTo(MatchType.INTERNAL);
        assertThat(format.teamCount()).isEqualTo(2);
        assertThat(format.requiredGoalkeepers()).isEqualTo(2);
    }

    @Test
    void shouldAcceptValidInternalConfigurations() {
        assertInternal(2, 2);
        assertInternal(3, 3);
        assertInternal(3, 4);
        assertInternal(4, 4);
    }

    @Test
    void shouldRejectInvalidInternalConfigurations() {
        assertThatThrownBy(() -> MatchFormatPolicy.resolve(MatchType.INTERNAL, null, 2))
                .isInstanceOf(MatchFormatPolicy.InvalidMatchFormatException.class);
        assertThatThrownBy(() -> MatchFormatPolicy.resolve(MatchType.INTERNAL, 1, 1))
                .isInstanceOf(MatchFormatPolicy.InvalidMatchFormatException.class);
        assertThatThrownBy(() -> MatchFormatPolicy.resolve(MatchType.INTERNAL, 2, 1))
                .isInstanceOf(MatchFormatPolicy.InvalidMatchFormatException.class);
        assertThatThrownBy(() -> MatchFormatPolicy.resolve(MatchType.INTERNAL, 3, 2))
                .isInstanceOf(MatchFormatPolicy.InvalidMatchFormatException.class);
    }

    @Test
    void shouldValidateExternalConfigurationsWithoutTeamCount() {
        assertThat(MatchFormatPolicy.resolve(MatchType.VERSUS_EXTERNAL, null, 1))
                .satisfies(format -> {
                    assertThat(format.teamCount()).isNull();
                    assertThat(format.requiredGoalkeepers()).isEqualTo(1);
                });
        assertThat(MatchFormatPolicy.resolve(MatchType.VERSUS_EXTERNAL, null, 2)
                .requiredGoalkeepers()).isEqualTo(2);
        assertThatThrownBy(() -> MatchFormatPolicy.resolve(MatchType.VERSUS_EXTERNAL, null, 0))
                .isInstanceOf(MatchFormatPolicy.InvalidMatchFormatException.class);
        assertThatThrownBy(() -> MatchFormatPolicy.resolve(MatchType.VERSUS_EXTERNAL, 2, 2))
                .isInstanceOf(MatchFormatPolicy.InvalidMatchFormatException.class);
    }

    private void assertInternal(int teamCount, int requiredGoalkeepers) {
        assertThat(MatchFormatPolicy.resolve(
                MatchType.INTERNAL,
                teamCount,
                requiredGoalkeepers)).satisfies(format -> {
                    assertThat(format.matchType()).isEqualTo(MatchType.INTERNAL);
                    assertThat(format.teamCount()).isEqualTo(teamCount);
                    assertThat(format.requiredGoalkeepers()).isEqualTo(requiredGoalkeepers);
                });
    }
}
