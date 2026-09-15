package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.onze.api.group.PlayerPosition;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGuestRepository extends JpaRepository<MatchGuest, UUID> {
    List<MatchGuest> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);
    Optional<MatchGuest> findByIdAndMatchId(UUID id, UUID matchId);
    long countByMatchId(UUID matchId);
    long countByMatchIdAndPrimaryPosition(UUID matchId, PlayerPosition primaryPosition);
}
