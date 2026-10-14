package com.onze.api.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchFoulEventRepository extends JpaRepository<MatchFoulEvent, UUID> {
    List<MatchFoulEvent> findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(UUID matchId);
    List<MatchFoulEvent> findAllByMatchIdIn(Collection<UUID> matchIds);
    Optional<MatchFoulEvent> findByIdAndMatchId(UUID id, UUID matchId);
    boolean existsByMatchIdAndGoalEventId(UUID matchId, UUID goalEventId);
    boolean existsByMatchIdAndShotEventId(UUID matchId, UUID shotEventId);
    void deleteAllByMatchId(UUID matchId);
}
