package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchShotEventRepository extends JpaRepository<MatchShotEvent, UUID> {
    List<MatchShotEvent> findAllByMatchIdOrderByCreatedAtDesc(UUID matchId);
    List<MatchShotEvent> findAllByMatchIdAndSideNumberAndOnTargetOrderByCreatedAtDesc(
            UUID matchId, int sideNumber, boolean onTarget);
    void deleteAllByMatchId(UUID matchId);
}
