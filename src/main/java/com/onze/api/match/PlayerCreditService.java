package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class PlayerCreditService {

    private final GroupPlayerCreditRepository creditRepository;
    private final FootballMatchRepository matchRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final MatchNotificationQueue notificationQueue;

    public PlayerCreditService(
            GroupPlayerCreditRepository creditRepository,
            FootballMatchRepository matchRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchNotificationQueue notificationQueue) {
        this.creditRepository = creditRepository;
        this.matchRepository = matchRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationQueue = notificationQueue;
    }

    public void addCredit(
            UUID groupId,
            UUID userId,
            BigDecimal amount,
            Instant now) {
        GroupPlayerCredit credit = requireCreditForUpdate(groupId, userId);
        credit.add(amount);
        reserveForNextMatch(groupId, userId, now);
    }

    public void reserveAvailableCreditsForGroup(UUID groupId, Instant now) {
        for (GroupPlayerCredit credit : creditRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId)) {
            if (credit.getBalance().signum() > 0) {
                reserveForNextMatch(groupId, credit.getUserId(), now);
            }
        }
    }

    public void reserveForNextMatch(UUID groupId, UUID userId, Instant now) {
        GroupPlayerCredit credit = creditRepository
                .findByGroupIdAndUserIdForUpdate(groupId, userId)
                .orElse(null);
        if (credit == null || credit.getBalance().signum() <= 0) {
            return;
        }

        List<FootballMatch> upcoming = matchRepository
                .findAllByGroupIdAndStatusAndPaymentAmountIsNotNullAndStartsAtAfterOrderByStartsAtAsc(
                        groupId,
                        MatchStatus.SCHEDULED,
                        now);
        for (FootballMatch candidate : upcoming) {
            MatchAttendance attendance = attendanceRepository
                    .findByMatchIdAndUserId(candidate.getId(), userId)
                    .orElse(null);
            if (attendance != null
                    && attendance.hasActiveCredit()
                    && !attendance.isCreditConsumed()) {
                return;
            }
        }

        for (FootballMatch candidate : upcoming) {
            MatchAttendance attendance = attendanceRepository
                    .findByMatchIdAndUserId(candidate.getId(), userId)
                    .orElse(null);
            if (attendance != null
                    && (attendance.getStatus() == AttendanceStatus.NOT_GOING
                            || attendance.hasActiveCredit()
                            || attendance.getPaymentStatus() == PaymentStatus.PAID
                            || attendance.getPaymentStatus() == PaymentStatus.REPORTED)) {
                continue;
            }
            if (attendance == null) {
                attendance = attendanceRepository.save(new MatchAttendance(
                        candidate.getId(),
                        userId,
                        AttendanceStatus.PENDING,
                        candidate.getPaymentAmount()));
            }

            BigDecimal amount = credit.getBalance().min(candidate.getPaymentAmount());
            attendance.reserveCredit(amount, candidate.getPaymentAmount(), now);
            if (attendance.getStatus() == AttendanceStatus.GOING) {
                credit.consume(amount);
                attendance.consumeCredit(now);
            }
            notificationQueue.enqueue(
                    candidate.getId(),
                    userId,
                    MatchNotificationType.CREDIT_APPLIED,
                    "match:" + candidate.getId() + ":credit-applied:"
                            + userId + ":" + now.toEpochMilli(),
                    now);
            return;
        }
    }

    public void consumeReservation(UUID groupId, MatchAttendance attendance, Instant now) {
        if (!attendance.hasActiveCredit() || attendance.isCreditConsumed()) {
            return;
        }
        GroupPlayerCredit credit = creditRepository
                .findByGroupIdAndUserIdForUpdate(groupId, attendance.getUserId())
                .orElseThrow(() -> new IllegalStateException("Player credit account was not found"));
        credit.consume(attendance.getCreditAppliedAmount());
        attendance.consumeCredit(now);
        reserveForNextMatch(groupId, attendance.getUserId(), now);
    }

    public boolean releaseReservation(UUID groupId, MatchAttendance attendance, Instant now) {
        if (!attendance.hasActiveCredit()) {
            return false;
        }
        boolean consumed = attendance.isCreditConsumed();
        BigDecimal amount = attendance.getCreditAppliedAmount();
        if (consumed) {
            GroupPlayerCredit credit = requireCreditForUpdate(groupId, attendance.getUserId());
            credit.add(amount);
        }
        attendance.releaseCredit(now);
        return true;
    }

    public List<PlayerCreditSnapshot> listForGroup(UUID groupId, Instant now) {
        List<FootballMatch> upcoming = matchRepository
                .findAllByGroupIdAndStatusAndPaymentAmountIsNotNullAndStartsAtAfterOrderByStartsAtAsc(
                        groupId,
                        MatchStatus.SCHEDULED,
                        now);
        List<PlayerCreditSnapshot> result = new ArrayList<>();

        for (GroupPlayerCredit credit : creditRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId)) {
            MatchAttendance allocation = null;
            FootballMatch allocatedMatch = null;
            for (FootballMatch match : upcoming) {
                MatchAttendance attendance = attendanceRepository
                        .findByMatchIdAndUserId(match.getId(), credit.getUserId())
                        .orElse(null);
                if (attendance != null && attendance.hasActiveCredit()) {
                    if (allocation == null
                            || (allocation.isCreditConsumed() && !attendance.isCreditConsumed())) {
                        allocation = attendance;
                        allocatedMatch = match;
                    }
                    if (!attendance.isCreditConsumed()) {
                        break;
                    }
                }
            }

            BigDecimal allocatedAmount = allocation == null
                    ? BigDecimal.ZERO
                    : allocation.getCreditAppliedAmount();
            BigDecimal availableAmount = credit.getBalance();
            CreditAllocationStatus allocationStatus = null;
            if (allocation != null) {
                allocationStatus = allocation.isCreditConsumed()
                        ? CreditAllocationStatus.APPLIED
                        : CreditAllocationStatus.RESERVED;
                if (!allocation.isCreditConsumed()) {
                    availableAmount = availableAmount.subtract(allocatedAmount).max(BigDecimal.ZERO);
                }
            }

            if (availableAmount.signum() > 0 || allocatedAmount.signum() > 0) {
                result.add(new PlayerCreditSnapshot(
                        credit.getUserId(),
                        availableAmount,
                        allocatedAmount,
                        allocationStatus,
                        allocatedMatch == null ? null : allocatedMatch.getId(),
                        allocatedMatch == null ? null : allocatedMatch.getStartsAt()));
            }
        }
        return List.copyOf(result);
    }

    private GroupPlayerCredit requireCreditForUpdate(UUID groupId, UUID userId) {
        return creditRepository.findByGroupIdAndUserIdForUpdate(groupId, userId)
                .orElseGet(() -> creditRepository.save(new GroupPlayerCredit(groupId, userId)));
    }

    public record PlayerCreditSnapshot(
            UUID userId,
            BigDecimal availableAmount,
            BigDecimal allocatedAmount,
            CreditAllocationStatus allocationStatus,
            UUID allocatedMatchId,
            Instant allocatedMatchStartsAt) {
    }
}
