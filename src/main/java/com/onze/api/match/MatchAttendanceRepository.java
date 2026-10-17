package com.onze.api.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchAttendanceRepository extends JpaRepository<MatchAttendance, UUID> {
    List<MatchAttendance> findAllByMatchIdInAndStatusOrderByMatchIdAscCreatedAtAsc(
            Collection<UUID> matchIds,
            AttendanceStatus status);

    List<MatchAttendance> findAllByMatchIdOrderByCreatedAtAsc(UUID matchId);

    Optional<MatchAttendance> findByMatchIdAndUserId(UUID matchId, UUID userId);

    List<MatchAttendance> findAllByPaymentSettlementStatusIn(
            Collection<PaymentSettlementStatus> statuses);

    long countByMatchIdAndStatus(UUID matchId, AttendanceStatus status);

    long countByMatchIdAndStatusAndGoalkeeperTrue(UUID matchId, AttendanceStatus status);

    List<MatchAttendance> findAllByMatchIdAndStatusOrderByWaitlistOrderAsc(
            UUID matchId, AttendanceStatus status);

    @Query(value = "select nextval('match_waitlist_order_seq')", nativeQuery = true)
    long nextWaitlistOrder();
}
