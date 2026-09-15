package com.onze.api.technical;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.onze.api.group.PlayerPosition;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TechnicalConfigurationController {

    @GetMapping("/api/technical/configuration")
    public TechnicalConfigurationResponse get() {
        Map<PlayerPosition, List<PlayerSkill>> positions = new EnumMap<>(PlayerPosition.class);
        for (PlayerPosition position : PlayerPosition.values()) {
            positions.put(position, TechnicalRatingPolicy.essentialSkills(position)
                    .stream().sorted().toList());
        }
        Map<FutsalRole, List<PlayerSkill>> futsal = new EnumMap<>(FutsalRole.class);
        for (FutsalRole role : FutsalRole.values()) {
            futsal.put(role, TechnicalRatingPolicy.essentialSkills(role)
                    .stream().sorted().toList());
        }
        return new TechnicalConfigurationResponse(
                Map.copyOf(positions), Map.copyOf(futsal), 1, 10, 5);
    }

    public record TechnicalConfigurationResponse(
            Map<PlayerPosition, List<PlayerSkill>> positionImportantSkills,
            Map<FutsalRole, List<PlayerSkill>> futsalImportantSkills,
            int minimumRating,
            int maximumRating,
            int pointsPerRatingUnit) {
    }
}
