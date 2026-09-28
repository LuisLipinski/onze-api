package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyMatchWindowService {

    static final int UPCOMING_MATCH_COUNT = 4;
    private static final int MAX_SKIPPED_OCCURRENCES = 520;

    private final FootballMatchRepository matchRepository;
    private final MatchSeriesRepository seriesRepository;
    private final PlayerCreditService playerCreditService;

    public WeeklyMatchWindowService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            PlayerCreditService playerCreditService) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.playerCreditService = playerCreditService;
    }

    @Transactional
    public int ensureAllActiveSeries(Instant now) {
        int created = 0;
        for (MatchSeries series : seriesRepository.findAllByActiveTrueOrderByIdAsc()) {
            created += ensureForSeries(series.getId(), now);
        }
        return created;
    }

    @Transactional
    public int ensureForMatch(FootballMatch match, Instant now) {
        if (match.getSeriesId() == null) {
            return 0;
        }
        return ensureForSeries(match.getSeriesId(), now);
    }

    @Transactional
    public int ensureForSeries(UUID seriesId, Instant now) {
        MatchSeries series = seriesRepository.findByIdForUpdate(seriesId).orElse(null);
        if (series == null || !series.isActive()) {
            return 0;
        }

        long upcomingCount = matchRepository
                .countBySeriesIdAndStatusAndStartsAtAfter(
                        seriesId,
                        MatchStatus.SCHEDULED,
                        now)
                + matchRepository.countBySeriesIdAndStatus(
                        seriesId,
                        MatchStatus.IN_PROGRESS);
        if (upcomingCount >= UPCOMING_MATCH_COUNT) {
            return 0;
        }

        FootballMatch latest = matchRepository
                .findFirstBySeriesIdOrderByOccurrenceNumberDesc(seriesId)
                .orElse(null);
        if (latest == null) {
            return 0;
        }

        int created = 0;
        int skipped = 0;
        while (upcomingCount < UPCOMING_MATCH_COUNT) {
            FootballMatch candidate = MatchRecurrenceSupport.nextOccurrence(latest, series);
            latest = candidate;
            if (!candidate.getStartsAt().isAfter(now)) {
                skipped++;
                if (skipped >= MAX_SKIPPED_OCCURRENCES) {
                    break;
                }
                continue;
            }

            latest = matchRepository.save(candidate);
            upcomingCount++;
            created++;
        }

        if (created > 0) {
            playerCreditService.reserveAvailableCreditsForGroup(series.getGroupId(), now);
        }
        return created;
    }
}
