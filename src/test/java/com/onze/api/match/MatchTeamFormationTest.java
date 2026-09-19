package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MatchTeamFormationTest {

    @Test
    void fut7UsesTwoThreeOneWithCentralMidfielder() {
        List<String> roles = MatchTeamService.outfieldTemplate(MatchModality.FUT7);

        assertEquals(6, roles.size());
        assertEquals(1, roles.stream().filter("RIGHT_DEFENDER"::equals).count());
        assertEquals(1, roles.stream().filter("LEFT_DEFENDER"::equals).count());
        assertEquals(1, roles.stream().filter("RIGHT_MIDFIELDER"::equals).count());
        assertEquals(1, roles.stream().filter("CENTRAL_MIDFIELDER"::equals).count());
        assertEquals(1, roles.stream().filter("LEFT_MIDFIELDER"::equals).count());
        assertEquals(1, roles.stream().filter("CENTER_FORWARD"::equals).count());
        assertFalse(roles.contains("RIGHT_WINGER"));
    }

    @Test
    void blocksFormationBelowMinimumWithoutExplicitApproval() {
        FootballMatch match = matchWithMinimum(14);

        assertFalse(MatchTeamService.minimumPlayersSatisfied(match, 13));
        assertTrue(MatchTeamService.minimumPlayersSatisfied(match, 14));
    }

    @Test
    void releasesFormationBelowMinimumOnlyAfterExplicitApprovalForOccurrence() {
        FootballMatch match = matchWithMinimum(14);

        assertFalse(MatchTeamService.minimumPlayersSatisfied(match, 10));

        match.approveBelowMinimum();

        assertTrue(MatchTeamService.minimumPlayersSatisfied(match, 10));
    }

    private FootballMatch matchWithMinimum(int minimumPlayers) {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        return new FootballMatch(
                groupId,
                null,
                null,
                Instant.parse("2026-09-20T21:00:00Z"),
                "UTC",
                "Arena Onze",
                20,
                MatchType.INTERNAL,
                2,
                2,
                MatchModality.FUT7,
                minimumPlayers,
                null,
                null,
                true,
                null,
                Instant.parse("2026-09-18T12:00:00Z"),
                Instant.parse("2026-09-18T12:00:00Z"),
                Instant.parse("2026-09-19T12:00:00Z"),
                null,
                creatorId);
    }

    @Test
    void fieldAndFutsalTemplatesRemainUnchanged() {
        assertEquals(List.of(
                "CENTER_DEFENDER", "CENTRAL_MIDFIELDER", "CENTER_FORWARD",
                "RIGHT_BACK", "DEFENSIVE_MIDFIELDER", "RIGHT_WINGER",
                "LEFT_BACK", "PLAYMAKER", "LEFT_WINGER", "CENTER_DEFENDER"),
                MatchTeamService.outfieldTemplate(MatchModality.FIELD));
        assertEquals(List.of(
                "FIXO", "RIGHT_WINGER_FUTSAL", "PIVOT", "LEFT_WINGER_FUTSAL"),
                MatchTeamService.outfieldTemplate(MatchModality.FUTSAL));
    }
}
