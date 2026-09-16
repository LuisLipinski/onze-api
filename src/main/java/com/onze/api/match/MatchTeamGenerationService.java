package com.onze.api.match;

import java.util.UUID;

import com.onze.api.match.MatchTeamDiversityService.DiversityResult;
import com.onze.api.match.TeamModels.MatchTeamsResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchTeamGenerationService {

    private final MatchTeamService teamService;
    private final MatchTeamDiversityService diversityService;

    public MatchTeamGenerationService(
            MatchTeamService teamService,
            MatchTeamDiversityService diversityService) {
        this.teamService = teamService;
        this.diversityService = diversityService;
    }

    @Transactional
    public MatchTeamsResponse generate(String authenticatedUserId, UUID matchId) {
        MatchTeamsResponse previous = teamService.get(authenticatedUserId, matchId);
        MatchTeamsResponse generated = teamService.generate(authenticatedUserId, matchId);
        DiversityResult diversity = diversityService.apply(matchId, generated, previous);
        MatchTeamsResponse response = diversity.changedFromGenerated()
                ? teamService.get(authenticatedUserId, matchId)
                : generated;
        return response.withGenerationNotice(diversity.notice());
    }
}
