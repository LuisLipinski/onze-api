package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

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
