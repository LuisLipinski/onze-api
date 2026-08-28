package com.onze.api.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAttendanceRepository extends JpaRepository<MatchAttendance, UUID> {

    List<MatchAttendance> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);

    Optional<MatchAttendance> findByMatchIdAndUserId(UUID matchId, UUID userId);

    long countByMatchIdAndStatus(UUID matchId, AttendanceStatus status);
}
