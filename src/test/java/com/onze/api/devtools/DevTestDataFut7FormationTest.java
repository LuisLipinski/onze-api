package com.onze.api.devtools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.onze.api.group.PlayerPosition;
import com.onze.api.match.MatchModality;

import org.junit.jupiter.api.Test;

class DevTestDataFut7FormationTest {

    @Test
    void balancedFut7MassMatchesTwoThreeOneProfile() {
        List<PlayerPosition> positions = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(number -> DevTestDataScenarioPolicy.profile(
                        number, 14, MatchModality.FUT7, DevTestDataScenario.BALANCED).primaryPosition())
                .toList();

        assertEquals(List.of(
                PlayerPosition.GOALKEEPER,
                PlayerPosition.RIGHT_DEFENDER,
                PlayerPosition.LEFT_DEFENDER,
                PlayerPosition.RIGHT_MIDFIELDER,
                PlayerPosition.CENTRAL_MIDFIELDER,
                PlayerPosition.LEFT_MIDFIELDER,
                PlayerPosition.CENTER_FORWARD), positions);
    }
}
