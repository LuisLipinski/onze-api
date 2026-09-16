package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchTeamGenerationHistoryRepository
        extends JpaRepository<MatchTeamGenerationHistory, UUID> {

    List<MatchTeamGenerationHistory> findAllByMatchIdOrderByGenerationNumberDesc(UUID matchId);

    List<MatchTeamGenerationHistory> findAllByGroupIdOrderByCreatedAtDesc(UUID groupId);
}
