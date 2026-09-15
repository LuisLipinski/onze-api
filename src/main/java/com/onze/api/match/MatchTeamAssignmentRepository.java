package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchTeamAssignmentRepository extends JpaRepository<MatchTeamAssignment, UUID> {
    List<MatchTeamAssignment> findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(UUID matchId);
    Optional<MatchTeamAssignment> findByIdAndMatchId(UUID id, UUID matchId);
    long countByMatchId(UUID matchId);
    void deleteAllByMatchId(UUID matchId);
}
