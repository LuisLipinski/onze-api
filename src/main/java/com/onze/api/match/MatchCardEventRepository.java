package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchCardEventRepository extends JpaRepository<MatchCardEvent, UUID> {
    List<MatchCardEvent> findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(UUID matchId);
    Optional<MatchCardEvent> findByIdAndMatchId(UUID id, UUID matchId);
    boolean existsByMatchIdAndPlayerAssignmentIdAndCardType(
            UUID matchId, UUID playerAssignmentId, MatchCardType cardType);
    long countByMatchIdAndPlayerAssignmentIdAndCardType(
            UUID matchId, UUID playerAssignmentId, MatchCardType cardType);
    void deleteAllByMatchId(UUID matchId);
}
