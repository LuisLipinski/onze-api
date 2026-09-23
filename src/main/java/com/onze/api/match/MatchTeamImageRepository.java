package com.onze.api.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchTeamImageRepository extends JpaRepository<MatchTeamImage, UUID> {
    List<MatchTeamImage> findAllByMatchIdOrderByTeamNumberAsc(UUID matchId);
    List<MatchTeamImage> findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(Collection<UUID> matchIds);
    Optional<MatchTeamImage> findByMatchIdAndTeamNumber(UUID matchId, int teamNumber);
}
