package com.onze.api.match;

import com.onze.api.group.PlayerPosition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTeamServiceGoalkeeperPriorityTest {

    @Test
    void shouldPrioritizeRentalGoalkeeperOverEmergencyMember() {
        int rental = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.RENTAL_GOALKEEPER,
                PlayerPosition.GOALKEEPER,
                null);
        int emergencyMember = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.MEMBER,
                PlayerPosition.DEFENDER,
                null);

        assertThat(rental).isLessThan(emergencyMember);
    }

    @Test
    void shouldKeepGoalkeeperPriorityDeterministicAcrossAllSupportedSources() {
        int rental = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.RENTAL_GOALKEEPER,
                PlayerPosition.GOALKEEPER,
                null);
        int explicitlyAssignedMember = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.MEMBER,
                PlayerPosition.DEFENDER,
                null);
        int primaryGoalkeeper = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.MEMBER,
                PlayerPosition.GOALKEEPER,
                null);
        int secondaryGoalkeeper = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.MEMBER,
                PlayerPosition.DEFENDER,
                PlayerPosition.GOALKEEPER);
        int unsupportedFallback = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.GUEST,
                PlayerPosition.DEFENDER,
                null);

        assertThat(rental).isLessThan(explicitlyAssignedMember);
        assertThat(explicitlyAssignedMember).isLessThan(primaryGoalkeeper);
        assertThat(primaryGoalkeeper).isLessThan(secondaryGoalkeeper);
        assertThat(secondaryGoalkeeper).isLessThan(unsupportedFallback);
    }

    @Test
    void shouldGiveEveryRentalGoalkeeperTheHighestPriority() {
        int firstRental = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.RENTAL_GOALKEEPER,
                PlayerPosition.GOALKEEPER,
                null);
        int secondRental = MatchTeamService.goalkeeperPriority(
                TeamParticipantType.RENTAL_GOALKEEPER,
                PlayerPosition.GOALKEEPER,
                PlayerPosition.DEFENDER);

        assertThat(firstRental).isZero();
        assertThat(secondRental).isZero();
    }
}
