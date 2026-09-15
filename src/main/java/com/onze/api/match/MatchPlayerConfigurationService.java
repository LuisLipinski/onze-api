package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.onze.api.match.MatchModels.MatchResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPlayerConfigurationService {

    private final FootballMatchRepository matchRepository;
    private final MatchSeriesRepository seriesRepository;
    private final MatchService matchService;
    private final Clock clock;

    public MatchPlayerConfigurationService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            MatchService matchService,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.matchService = matchService;
        this.clock = clock;
    }

    @Transactional
    public MatchResponse update(
            String authenticatedUserId,
            UUID matchId,
            MatchModality modality,
            int minimumPlayers,
            int maxPlayers) {
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        match.updateMaximumPlayers(maxPlayers);

        Instant now = clock.instant();
        if (match.getSeriesId() != null) {
            MatchSeries series = seriesRepository.findById(match.getSeriesId())
                    .orElseThrow(MatchService.MatchSeriesNotFoundException::new);
            series.updateMaximumPlayers(maxPlayers);
            matchRepository.findAllBySeriesIdAndStatusAndStartsAtAfter(
                            series.getId(), MatchStatus.SCHEDULED, now)
                    .forEach(item -> item.updateMaximumPlayers(maxPlayers));
        }

        return matchService.updatePlayerConfiguration(
                authenticatedUserId,
                matchId,
                modality,
                minimumPlayers);
    }
}
