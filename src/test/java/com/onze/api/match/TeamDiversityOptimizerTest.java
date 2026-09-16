package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.onze.api.match.TeamDiversityOptimizer.Decision;
import com.onze.api.match.TeamDiversityOptimizer.WeightedHistory;
import com.onze.api.match.TeamModels.MatchTeamsResponse;
import com.onze.api.match.TeamModels.TeamAssignmentResponse;
import com.onze.api.match.TeamModels.TeamResponse;
import com.onze.api.technical.ScoreSource;

import org.junit.jupiter.api.Test;

class TeamDiversityOptimizerTest {

    private static final UUID MATCH_ID = uuid("match");

    @Test
    void regenerationChoosesDifferentBalancedCombination() {
        MatchTeamsResponse generated = balancedResponse();
        String current = TeamDiversityOptimizer.signature(generated);

        Decision decision = TeamDiversityOptimizer.choose(
                generated,
                current,
                List.of(current),
                List.of(),
                MATCH_ID,
                2);

        assertTrue(decision.changedFromGenerated());
        assertFalse(decision.repeatedPrevious());
        assertFalse(decision.signature().equals(current));
    }

    @Test
    void newMatchAvoidsExactRecentDivisionWhenAlternativeIsBalanced() {
        MatchTeamsResponse generated = balancedResponse();
        String recent = TeamDiversityOptimizer.signature(generated);

        Decision decision = TeamDiversityOptimizer.choose(
                generated,
                null,
                List.of(),
                List.of(new WeightedHistory(recent, 5)),
                uuid("next-match"),
                1);

        assertTrue(decision.changedFromGenerated());
        assertFalse(decision.repeatedRecent());
        assertFalse(decision.signature().equals(recent));
    }

    @Test
    void swappingOnlyTeamNumbersIsNotAUniqueFormation() {
        MatchTeamsResponse original = balancedResponse();
        MatchTeamsResponse inverted = new MatchTeamsResponse(
                original.matchId(),
                original.modality(),
                original.teamCount(),
                original.confirmedPlayers(),
                original.minimumPlayers(),
                original.idealPlayers(),
                original.reducedTeams(),
                original.technicalDetailsVisible(),
                List.of(
                        new TeamResponse(1, 30, 4, 0, original.teams().get(1).assignments()),
                        new TeamResponse(2, 30, 4, 0, original.teams().get(0).assignments())));

        assertEquals(
                TeamDiversityOptimizer.signature(original),
                TeamDiversityOptimizer.signature(inverted));
    }

    @Test
    void reportsNoAlternativeWhenOnlyChangeIsFullTeamInversion() {
        MatchTeamsResponse response = new MatchTeamsResponse(
                MATCH_ID,
                MatchModality.FUT7,
                2,
                2,
                2,
                14,
                true,
                true,
                List.of(
                        new TeamResponse(1, 30, 1, 0, List.of(
                                assignment("one", "GOALKEEPER", 30))),
                        new TeamResponse(2, 30, 1, 0, List.of(
                                assignment("two", "GOALKEEPER", 30)))));
        String current = TeamDiversityOptimizer.signature(response);

        Decision decision = TeamDiversityOptimizer.choose(
                response,
                current,
                List.of(current),
                List.of(),
                MATCH_ID,
                2);

        assertFalse(decision.changedFromGenerated());
        assertTrue(decision.repeatedPrevious());
        assertEquals(current, decision.signature());
    }

    @Test
    void doesNotTradeBalanceForDiversityOutsideTolerance() {
        MatchTeamsResponse response = new MatchTeamsResponse(
                MATCH_ID,
                MatchModality.FUT7,
                2,
                4,
                4,
                14,
                true,
                true,
                List.of(
                        new TeamResponse(1, 30, 2, 0, List.of(
                                assignment("one-defense", "CENTER_DEFENDER", 50),
                                assignment("one-attack", "CENTER_FORWARD", 10))),
                        new TeamResponse(2, 30, 2, 0, List.of(
                                assignment("two-defense", "CENTER_DEFENDER", 10),
                                assignment("two-attack", "CENTER_FORWARD", 50)))));
        String current = TeamDiversityOptimizer.signature(response);

        Decision decision = TeamDiversityOptimizer.choose(
                response,
                current,
                List.of(current),
                List.of(),
                MATCH_ID,
                2);

        assertFalse(decision.changedFromGenerated());
        assertTrue(decision.repeatedPrevious());
        assertEquals(current, decision.signature());
    }

    private MatchTeamsResponse balancedResponse() {
        return new MatchTeamsResponse(
                MATCH_ID,
                MatchModality.FUT7,
                2,
                8,
                8,
                14,
                true,
                true,
                List.of(
                        new TeamResponse(1, 30, 4, 0, List.of(
                                assignment("one-gk", "GOALKEEPER", 30),
                                assignment("one-defense", "CENTER_DEFENDER", 30),
                                assignment("one-midfield", "CENTRAL_MIDFIELDER", 30),
                                assignment("one-attack", "CENTER_FORWARD", 30))),
                        new TeamResponse(2, 30, 4, 0, List.of(
                                assignment("two-gk", "GOALKEEPER", 30),
                                assignment("two-defense", "CENTER_DEFENDER", 30),
                                assignment("two-midfield", "CENTRAL_MIDFIELDER", 30),
                                assignment("two-attack", "CENTER_FORWARD", 30)))));
    }

    private TeamAssignmentResponse assignment(String key, String role, int score) {
        return new TeamAssignmentResponse(
                uuid(key + "-assignment"),
                TeamParticipantType.MEMBER,
                uuid(key + "-participant"),
                key,
                role,
                score,
                100,
                ScoreSource.REAL,
                TeamPositionOrigin.PRIMARY,
                TeamAssignmentReason.PRIMARY_POSITION,
                false);
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
