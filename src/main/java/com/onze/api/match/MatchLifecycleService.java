package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchLifecycleService {

    private static final int MAX_CATCH_UP_ROUNDS = 104;

    private final FootballMatchRepository matchRepository;
    private final MatchSeriesRepository seriesRepository;
    private final MatchNotificationJobRepository notificationJobRepository;
    private final Clock clock;

    public MatchLifecycleService(
            FootballMatchRepository matchRepository,
            MatchSeriesRepository seriesRepository,
            MatchNotificationJobRepository notificationJobRepository,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.seriesRepository = seriesRepository;
        this.notificationJobRepository = notificationJobRepository;
        this.clock = clock;
    }

    @Transactional
    public int openDueAttendances() {
        Instant now = clock.instant();
        int opened = 0;

        for (int round = 0; round < MAX_CATCH_UP_ROUNDS; round++) {
            List<FootballMatch> dueMatches = matchRepository
                    .findTop50ByStatusAndAttendanceOpenedAtIsNullAndAttendanceOpensAtLessThanEqualOrderByAttendanceOpensAtAsc(
                            MatchStatus.SCHEDULED,
                            now);
            if (dueMatches.isEmpty()) {
                break;
            }

            int openedThisRound = 0;
            for (FootballMatch due : dueMatches) {
                FootballMatch match = matchRepository.findByIdForUpdate(due.getId()).orElse(null);
                if (match == null
                        || match.getStatus() != MatchStatus.SCHEDULED
                        || match.getAttendanceOpenedAt() != null
                        || match.getAttendanceOpensAt().isAfter(now)) {
                    continue;
                }

                match.openAttendance(now);
                if (match.getStartsAt().isAfter(now)) {
                    notificationJobRepository.save(new MatchNotificationJob(
                            match.getId(),
                            MatchNotificationType.ATTENDANCE_OPENED,
                            now));
                }
                generateNextOccurrenceIfNeeded(match);
                opened++;
                openedThisRound++;
            }

            if (openedThisRound == 0) {
                break;
            }
        }

        return opened;
    }

    private void generateNextOccurrenceIfNeeded(FootballMatch match) {
        if (match.getSeriesId() == null) {
            return;
        }

        MatchSeries series = seriesRepository.findById(match.getSeriesId()).orElse(null);
        if (series == null || !series.isActive()) {
            return;
        }

        FootballMatch latest = matchRepository
                .findFirstBySeriesIdOrderByOccurrenceNumberDesc(series.getId())
                .orElse(match);
        if (!latest.getId().equals(match.getId())) {
            return;
        }

        matchRepository.save(MatchRecurrenceSupport.nextOccurrence(match, series));
    }
}
