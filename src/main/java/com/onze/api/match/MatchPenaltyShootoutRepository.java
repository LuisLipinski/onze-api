package com.onze.api.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPenaltyShootoutRepository extends JpaRepository<MatchPenaltyShootout, UUID> {
    Optional<MatchPenaltyShootout> findByMatchId(UUID matchId);
    List<MatchPenaltyShootout> findAllByMatchIdIn(Collection<UUID> matchIds);
    void deleteByMatchId(UUID matchId);
}
