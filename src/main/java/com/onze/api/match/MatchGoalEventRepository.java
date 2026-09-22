package com.onze.api.match;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGoalEventRepository extends JpaRepository<MatchGoalEvent, UUID> {
    List<MatchGoalEvent> findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(UUID matchId);
    Optional<MatchGoalEvent> findByIdAndMatchId(UUID id, UUID matchId);
    void deleteAllByMatchId(UUID matchId);
}
