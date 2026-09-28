package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPenaltyTakerRepository extends JpaRepository<MatchPenaltyTaker, UUID> {
    List<MatchPenaltyTaker> findAllByMatchIdOrderByTeamNumberAscKickOrderAsc(UUID matchId);
    Optional<MatchPenaltyTaker> findByMatchIdAndTeamNumberAndKickOrder(UUID matchId, int teamNumber, int kickOrder);
    void deleteAllByMatchId(UUID matchId);
}
