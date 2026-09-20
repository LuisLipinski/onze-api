package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchTeamAssignmentRepository extends JpaRepository<MatchTeamAssignment, UUID> {
    List<MatchTeamAssignment> findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(UUID matchId);
    Optional<MatchTeamAssignment> findByIdAndMatchId(UUID id, UUID matchId);
    long countByMatchId(UUID matchId);

    @Modifying(flushAutomatically = true)
    @Query("delete from MatchTeamAssignment assignment where assignment.matchId = :matchId")
    void deleteAllByMatchId(@Param("matchId") UUID matchId);

    @Modifying(flushAutomatically = true)
    @Query("delete from MatchTeamAssignment assignment where assignment.participantType = :type and assignment.participantId in :participantIds")
    void deleteAllByParticipantTypeAndParticipantIdIn(
            @Param("type") TeamParticipantType type,
            @Param("participantIds") Collection<UUID> participantIds);
}
