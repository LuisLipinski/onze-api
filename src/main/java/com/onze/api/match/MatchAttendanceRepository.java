package com.onze.api.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAttendanceRepository extends JpaRepository<MatchAttendance, UUID> {

    List<MatchAttendance> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);

    Optional<MatchAttendance> findByMatchIdAndUserId(UUID matchId, UUID userId);

    List<MatchAttendance> findAllByPaymentSettlementStatusIn(
            Collection<PaymentSettlementStatus> statuses);

    long countByMatchIdAndStatus(UUID matchId, AttendanceStatus status);
}
