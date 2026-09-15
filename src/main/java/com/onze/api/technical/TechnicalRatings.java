package com.onze.api.technical;

import java.util.EnumMap;
import java.util.Map;

public final class TechnicalRatings {

    private TechnicalRatings() {
    }

    public static Map<PlayerSkill, Integer> normalize(Map<PlayerSkill, Integer> requested) {
        if (requested == null || requested.size() > PlayerSkill.values().length) {
            throw new InvalidTechnicalRatingException();
        }
        Map<PlayerSkill, Integer> normalized = new EnumMap<>(PlayerSkill.class);
        requested.forEach((skill, rating) -> {
            if (skill == null) {
                throw new InvalidTechnicalRatingException();
            }
            if (rating == null) {
                return;
            }
            if (rating < 1 || rating > 10) {
                throw new InvalidTechnicalRatingException();
            }
            normalized.put(skill, rating);
        });
        return normalized;
    }

    public static final class InvalidTechnicalRatingException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
