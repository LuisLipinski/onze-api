package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRentalGoalkeeperRepository extends JpaRepository<MatchRentalGoalkeeper, UUID> {

    List<MatchRentalGoalkeeper> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);

    Optional<MatchRentalGoalkeeper> findByIdAndMatchId(UUID id, UUID matchId);

    long countByMatchId(UUID matchId);
}
