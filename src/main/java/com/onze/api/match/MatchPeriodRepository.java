package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPeriodRepository extends JpaRepository<MatchPeriod, UUID> {
    List<MatchPeriod> findAllByMatchIdOrderByStartedAtAsc(UUID matchId);
    Optional<MatchPeriod> findFirstByMatchIdAndEndedAtIsNullOrderByStartedAtDesc(UUID matchId);
    long countByMatchIdAndPeriodType(UUID matchId, MatchPeriodType periodType);
    void deleteAllByMatchId(UUID matchId);
}
