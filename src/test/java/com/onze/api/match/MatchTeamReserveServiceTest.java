package com.onze.api.match;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.onze.api.match.TeamModels.MatchTeamsResponse;
import com.onze.api.match.TeamModels.TeamAssignmentResponse;
import com.onze.api.match.TeamModels.TeamResponse;
import com.onze.api.technical.ScoreSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTeamReserveServiceTest {

    @Test
    void shouldReserveWeakestPlayerFromSurplusDefenseInField() {
        TeamAssignmentResponse weakestDefender = assignment("Defesa mais fraco", "CENTER_DEFENDER", 20);
        MatchTeamsResponse response = teams(
                MatchModality.FIELD,
                List.of(
                        assignment("Goleiro", "GOALKEEPER", 30),
                        assignment("LD", "RIGHT_BACK", 35),
                        assignment("Z1", "CENTER_DEFENDER", 42),
                        assignment("Z2", "CENTER_DEFENDER", 34),
                        assignment("LE", "LEFT_BACK", 33),
                        weakestDefender,
                        assignment("Volante", "DEFENSIVE_MIDFIELDER", 30),
                        assignment("Meia", "CENTRAL_MIDFIELDER", 31),
                        assignment("Armador", "PLAYMAKER", 32),
                        assignment("PD", "RIGHT_WINGER", 34),
                        assignment("CA", "CENTER_FORWARD", 36),
                        assignment("PE", "LEFT_WINGER", 35)));

        Set<UUID> reserves = MatchTeamReserveService.automaticReserveIds(response);

        assertThat(reserves).containsExactly(weakestDefender.id());
    }

    @Test
    void shouldReserveWeakestDefenderWhenFut7HasThreeDifferentDefensiveRoles() {
        TeamAssignmentResponse weakestDefender = assignment("Defesa mais fraco", "CENTER_DEFENDER", 18);
        MatchTeamsResponse response = teams(
                MatchModality.FUT7,
                List.of(
                        assignment("Goleiro", "GOALKEEPER", 28),
                        assignment("Defesa D", "RIGHT_DEFENDER", 32),
                        assignment("Defesa E", "LEFT_DEFENDER", 29),
                        weakestDefender,
                        assignment("Meia D", "RIGHT_MIDFIELDER", 31),
                        assignment("Meia C", "CENTRAL_MIDFIELDER", 33),
                        assignment("Meia E", "LEFT_MIDFIELDER", 30),
                        assignment("Atacante", "CENTER_FORWARD", 35)));

        Set<UUID> reserves = MatchTeamReserveService.automaticReserveIds(response);

        assertThat(reserves).containsExactly(weakestDefender.id());
    }

    @Test
    void shouldNotCreateReserveWhenTeamDoesNotExceedModalityCapacity() {
        MatchTeamsResponse response = teams(
                MatchModality.FUT7,
                List.of(
                        assignment("Goleiro", "GOALKEEPER", 30),
                        assignment("Defesa", "RIGHT_DEFENDER", 30),
                        assignment("Meia 1", "RIGHT_MIDFIELDER", 30),
                        assignment("Meia 2", "CENTRAL_MIDFIELDER", 30),
                        assignment("Meia 3", "LEFT_MIDFIELDER", 30),
                        assignment("Meia 4", "PLAYMAKER", 30),
                        assignment("Atacante", "CENTER_FORWARD", 30)));

        assertThat(MatchTeamReserveService.automaticReserveIds(response)).isEmpty();
    }

    @Test
    void shouldNotCreateReserveWhenFutsalFormationHasExactlyFivePlayers() {
        MatchTeamsResponse response = teams(
                MatchModality.FUTSAL,
                List.of(
                        assignment("Goleiro", "GOALKEEPER", 30),
                        assignment("Fixo", "FIXO", 30),
                        assignment("Ala D", "RIGHT_WINGER_FUTSAL", 30),
                        assignment("Ala E", "LEFT_WINGER_FUTSAL", 30),
                        assignment("Pivo", "PIVOT", 30)));

        assertThat(MatchTeamReserveService.automaticReserveIds(response)).isEmpty();
    }

    private MatchTeamsResponse teams(
            MatchModality modality,
            List<TeamAssignmentResponse> assignments) {
        return new MatchTeamsResponse(
                UUID.randomUUID(),
                modality,
                1,
                assignments.size(),
                1,
                assignments.size(),
                false,
                true,
                null,
                List.of(new TeamResponse(1, 30, assignments.size(), 0, assignments)));
    }

    private TeamAssignmentResponse assignment(String name, String role, int overall) {
        return new TeamAssignmentResponse(
                UUID.randomUUID(),
                TeamParticipantType.MEMBER,
                UUID.randomUUID(),
                name,
                role,
                overall,
                100,
                ScoreSource.REAL,
                TeamPositionOrigin.PRIMARY,
                TeamAssignmentReason.PRIMARY_POSITION,
                false);
    }
}
