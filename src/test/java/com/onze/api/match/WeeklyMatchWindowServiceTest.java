package com.onze.api.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class WeeklyMatchWindowServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private FootballMatchRepository matchRepository;
    private MatchSeriesRepository seriesRepository;
    private PlayerCreditService playerCreditService;
    private WeeklyMatchWindowService service;
    private UUID seriesId;
    private UUID groupId;
    private UUID creatorId;
    private MatchSeries series;

    @BeforeEach
    void setUp() {
        matchRepository = mock(FootballMatchRepository.class);
        seriesRepository = mock(MatchSeriesRepository.class);
        playerCreditService = mock(PlayerCreditService.class);
        service = new WeeklyMatchWindowService(
                matchRepository,
                seriesRepository,
                playerCreditService);

        seriesId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        series = new MatchSeries(
                groupId,
                creatorId,
                "America/Sao_Paulo",
                "Arena Onze",
                18,
                MatchType.INTERNAL,
                2,
                2,
                MatchModality.FUT7,
                14,
                null,
                null,
                true,
                "Levar colete");
        ReflectionTestUtils.setField(series, "id", seriesId);
        when(seriesRepository.findByIdForUpdate(seriesId)).thenReturn(Optional.of(series));
        when(matchRepository.countBySeriesIdAndStatus(seriesId, MatchStatus.IN_PROGRESS))
                .thenReturn(0L);
        when(matchRepository.save(any(FootballMatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateFourUpcomingOccurrencesForANewWeeklySeries() {
        FootballMatch first = occurrence(1, Instant.parse("2026-09-03T23:30:00Z"));
        when(matchRepository.countBySeriesIdAndStatusAndStartsAtAfter(
                seriesId,
                MatchStatus.SCHEDULED,
                NOW)).thenReturn(1L);
        when(matchRepository.findFirstBySeriesIdOrderByOccurrenceNumberDesc(seriesId))
                .thenReturn(Optional.of(first));

        assertThat(service.ensureForSeries(seriesId, NOW)).isEqualTo(3);

        ArgumentCaptor<FootballMatch> created = ArgumentCaptor.forClass(FootballMatch.class);
        verify(matchRepository, org.mockito.Mockito.times(3)).save(created.capture());
        List<FootballMatch> occurrences = created.getAllValues();
        assertThat(occurrences)
                .extracting(FootballMatch::getOccurrenceNumber)
                .containsExactly(2, 3, 4);
        assertThat(occurrences)
                .extracting(FootballMatch::getStartsAt)
                .containsExactly(
                        first.getStartsAt().plusSeconds(7 * 24 * 60 * 60),
                        first.getStartsAt().plusSeconds(14 * 24 * 60 * 60),
                        first.getStartsAt().plusSeconds(21 * 24 * 60 * 60));
        verify(playerCreditService).reserveAvailableCreditsForGroup(groupId, NOW);
    }

    @Test
    void shouldNotCreateAnotherOccurrenceWhileFourMatchesRemainActive() {
        when(matchRepository.countBySeriesIdAndStatusAndStartsAtAfter(
                seriesId,
                MatchStatus.SCHEDULED,
                NOW)).thenReturn(3L);
        when(matchRepository.countBySeriesIdAndStatus(seriesId, MatchStatus.IN_PROGRESS))
                .thenReturn(1L);

        assertThat(service.ensureForSeries(seriesId, NOW)).isZero();

        verify(matchRepository, never()).findFirstBySeriesIdOrderByOccurrenceNumberDesc(seriesId);
        verify(matchRepository, never()).save(any(FootballMatch.class));
        verify(playerCreditService, never()).reserveAvailableCreditsForGroup(groupId, NOW);
    }

    @Test
    void shouldAppendTheFifthOccurrenceAfterTheFirstMatchFinishes() {
        FootballMatch fourth = occurrence(4, Instant.parse("2026-09-24T23:30:00Z"));
        when(matchRepository.countBySeriesIdAndStatusAndStartsAtAfter(
                seriesId,
                MatchStatus.SCHEDULED,
                NOW)).thenReturn(3L);
        when(matchRepository.findFirstBySeriesIdOrderByOccurrenceNumberDesc(seriesId))
                .thenReturn(Optional.of(fourth));

        assertThat(service.ensureForSeries(seriesId, NOW)).isEqualTo(1);

        ArgumentCaptor<FootballMatch> created = ArgumentCaptor.forClass(FootballMatch.class);
        verify(matchRepository).save(created.capture());
        assertThat(created.getValue().getOccurrenceNumber()).isEqualTo(5);
        assertThat(created.getValue().getStartsAt())
                .isEqualTo(Instant.parse("2026-10-01T23:30:00Z"));
        verify(playerCreditService).reserveAvailableCreditsForGroup(groupId, NOW);
    }

    private FootballMatch occurrence(int number, Instant startsAt) {
        return new FootballMatch(
                groupId,
                seriesId,
                number,
                startsAt,
                "America/Sao_Paulo",
                "Arena Onze",
                18,
                MatchType.INTERNAL,
                2,
                2,
                MatchModality.FUT7,
                14,
                null,
                null,
                true,
                "Levar colete",
                NOW,
                number == 1 ? NOW : null,
                startsAt.minusSeconds(60 * 60),
                null,
                creatorId);
    }
}
