package com.onze.api.match;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchCardEventRepository extends JpaRepository<MatchCardEvent, UUID> {
    List<MatchCardEvent> findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(UUID matchId);
    void deleteAllByMatchId(UUID matchId);
}
