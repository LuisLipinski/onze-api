package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveMatchScoreRepository extends JpaRepository<LiveMatchScore, UUID> {
    List<LiveMatchScore> findAllByMatchIdOrderBySideNumberAsc(UUID matchId);
    Optional<LiveMatchScore> findByMatchIdAndSideNumber(UUID matchId, int sideNumber);
}
