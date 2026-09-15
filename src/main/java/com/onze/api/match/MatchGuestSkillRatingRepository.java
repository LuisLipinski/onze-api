package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGuestSkillRatingRepository extends JpaRepository<MatchGuestSkillRating, UUID> {
    List<MatchGuestSkillRating> findAllByGuestId(UUID guestId);
    List<MatchGuestSkillRating> findAllByGuestIdIn(List<UUID> guestIds);
}
