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
    void shouldReserveWeakestPlayerWhenRoleHasMorePlayersThanFormationSlots() {
        TeamAssignmentResponse strongest = assignment("Forte", "CENTER_DEFENDER", 42);
        TeamAssignmentResponse medium = assignment("Medio", "CENTER_DEFENDER", 34);
        TeamAssignmentResponse weakest = assignment("Fraco", "CENTER_DEFENDER", 25);
        MatchTeamsResponse response = teams(
                MatchModality.FIELD,
                List.of(strongest, weakest, medium));

        Set<UUID> reserves = MatchTeamReserveService.automaticReserveIds(response);

        assertThat(reserves).containsExactly(weakest.id());
    }

    @Test
    void shouldKeepOnePlayerPerFut7RoleOnFieldAndReserveTheWeakerDuplicate() {
        TeamAssignmentResponse rightDefender = assignment("Titular", "RIGHT_DEFENDER", 31);
        TeamAssignmentResponse extraRightDefender = assignment("Reserva", "RIGHT_DEFENDER", 22);
        TeamAssignmentResponse goalkeeper = assignment("Goleiro", "GOALKEEPER", 28);
        MatchTeamsResponse response = teams(
                MatchModality.FUT7,
                List.of(extraRightDefender, goalkeeper, rightDefender));

        Set<UUID> reserves = MatchTeamReserveService.automaticReserveIds(response);

        assertThat(reserves).containsExactly(extraRightDefender.id());
    }

    @Test
    void shouldNotCreateReserveWhenFormationHasCapacityForEveryRole() {
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
