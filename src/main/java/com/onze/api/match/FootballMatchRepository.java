package com.onze.api.match;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FootballMatchRepository extends JpaRepository<FootballMatch, UUID> {

    List<FootballMatch> findAllByGroupIdInAndStatusAndStartsAtAfterOrderByStartsAtAsc(
            Collection<UUID> groupIds,
            MatchStatus status,
            Instant startsAt);

    List<FootballMatch> findAllByGroupIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
            UUID groupId,
            MatchStatus status,
            Instant startsAt);

    List<FootballMatch> findTop50ByStatusAndAttendanceOpenedAtIsNullAndAttendanceOpensAtLessThanEqualOrderByAttendanceOpensAtAsc(
            MatchStatus status,
            Instant attendanceOpensAt);

    Optional<FootballMatch> findFirstBySeriesIdOrderByOccurrenceNumberDesc(UUID seriesId);

    List<FootballMatch> findAllBySeriesIdAndStatusAndStartsAtAfter(
            UUID seriesId,
            MatchStatus status,
            Instant startsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select match from FootballMatch match where match.id = :id")
    Optional<FootballMatch> findByIdForUpdate(@Param("id") UUID id);
}
