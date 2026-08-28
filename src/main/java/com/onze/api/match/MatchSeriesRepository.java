package com.onze.api.match;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchSeriesRepository extends JpaRepository<MatchSeries, UUID> {
}
