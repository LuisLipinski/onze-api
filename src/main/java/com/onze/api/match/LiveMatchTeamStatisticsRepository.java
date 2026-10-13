package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveMatchTeamStatisticsRepository extends JpaRepository<LiveMatchTeamStatistics, UUID> {
    List<LiveMatchTeamStatistics> findAllByMatchIdOrderBySideNumberAsc(UUID matchId);
    Optional<LiveMatchTeamStatistics> findByMatchIdAndSideNumber(UUID matchId, int sideNumber);
    void deleteAllByMatchId(UUID matchId);
}
