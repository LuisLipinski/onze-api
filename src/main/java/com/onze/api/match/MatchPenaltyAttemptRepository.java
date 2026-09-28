package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPenaltyAttemptRepository extends JpaRepository<MatchPenaltyAttempt, UUID> {
    List<MatchPenaltyAttempt> findAllByMatchIdOrderBySequenceNumberAsc(UUID matchId);
    void deleteAllByMatchId(UUID matchId);
}
