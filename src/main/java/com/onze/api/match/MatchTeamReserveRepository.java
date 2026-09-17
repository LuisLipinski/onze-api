package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchTeamReserveRepository extends JpaRepository<MatchTeamReserve, UUID> {
    List<MatchTeamReserve> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MatchTeamReserve reserve where reserve.matchId = :matchId")
    void deleteAllByMatchId(@Param("matchId") UUID matchId);
}
