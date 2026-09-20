package com.onze.api.match;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGoalEventRepository extends JpaRepository<MatchGoalEvent, UUID> {
    void deleteAllByMatchId(UUID matchId);
}
