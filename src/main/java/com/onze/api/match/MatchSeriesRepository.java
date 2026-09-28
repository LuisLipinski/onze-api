package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchSeriesRepository extends JpaRepository<MatchSeries, UUID> {

    List<MatchSeries> findAllByActiveTrueOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select series from MatchSeries series where series.id = :id")
    Optional<MatchSeries> findByIdForUpdate(@Param("id") UUID id);
}
