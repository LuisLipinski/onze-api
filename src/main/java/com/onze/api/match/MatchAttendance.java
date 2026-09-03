package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "match_attendances",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_attendances_match_user",
                columnNames = {"match_id", "user_id"}))
public class MatchAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 24)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_reported_at")
    private Instant paymentReportedAt;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_settlement_status", length = 32)
    private PaymentSettlementStatus paymentSettlementStatus;

    @Column(name = "payment_settlement_requested_at")
    private Instant paymentSettlementRequestedAt;

    @Column(name = "payment_settlement_resolved_at")
    private Instant paymentSettlementResolvedAt;

    @Column(name = "credit_applied_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal creditAppliedAmount;

    @Column(name = "cash_amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal cashAmountDue;

    @Column(name = "cash_paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal cashPaidAmount;

    @Column(name = "credit_consumed_at")
    private Instant creditConsumedAt;

    @Column(name = "credit_returned_at")
    private Instant creditReturnedAt;

    @Column(name = "payment_deadline_removed_at")
    private Instant paymentDeadlineRemovedAt;

    @Column(name = "replacement_required_at")
    private Instant replacementRequiredAt;

    @Column(name = "replacement_user_id")
    private UUID replacementUserId;

    @Column(name = "replacement_filled_at")
    private Instant replacementFilledAt;

    @Column(name = "added_as_replacement_at")
    private Instant addedAsReplacementAt;

    @Column(name = "replacement_for_user_id")
    private UUID replacementForUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchAttendance() {
    }

    public MatchAttendance(
            UUID matchId,
            UUID userId,
            AttendanceStatus status,
            BigDecimal paymentAmount) {
        this.matchId = matchId;
        this.userId = userId;
        this.status = status;
        this.creditAppliedAmount = BigDecimal.ZERO;
        this.cashAmountDue = paymentAmount == null ? BigDecimal.ZERO : paymentAmount;
        this.cashPaidAmount = BigDecimal.ZERO;
        if (status != AttendanceStatus.NOT_GOING && paymentAmount != null) {
            this.paymentStatus = PaymentStatus.PENDING;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public Instant getPaymentReportedAt() {
        return paymentReportedAt;
    }

    public Instant getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public PaymentSettlementStatus getPaymentSettlementStatus() {
        return paymentSettlementStatus;
    }

    public Instant getPaymentSettlementRequestedAt() {
        return paymentSettlementRequestedAt;
    }

    public Instant getPaymentSettlementResolvedAt() {
        return paymentSettlementResolvedAt;
    }

    public BigDecimal getCreditAppliedAmount() {
        return creditAppliedAmount;
    }

    public BigDecimal getCashAmountDue() {
        return cashAmountDue;
    }

    public BigDecimal getCashPaidAmount() {
        return cashPaidAmount;
    }

    public Instant getCreditConsumedAt() {
        return creditConsumedAt;
    }

    public Instant getCreditReturnedAt() {
        return creditReturnedAt;
    }

    public Instant getPaymentDeadlineRemovedAt() {
        return paymentDeadlineRemovedAt;
    }

    public Instant getReplacementRequiredAt() {
        return replacementRequiredAt;
    }

    public UUID getReplacementUserId() {
        return replacementUserId;
    }

    public Instant getReplacementFilledAt() {
        return replacementFilledAt;
    }

    public Instant getAddedAsReplacementAt() {
        return addedAsReplacementAt;
    }

    public UUID getReplacementForUserId() {
        return replacementForUserId;
    }

    public boolean isAwaitingReplacement() {
        return replacementRequiredAt != null && replacementFilledAt == null;
    }

    public boolean requiresAdministratorToRejoin() {
        return replacementRequiredAt != null;
    }

    public boolean wasAddedAsReplacement() {
        return addedAsReplacementAt != null;
    }

    public boolean hasActiveCredit() {
        return creditAppliedAmount.signum() > 0 && creditReturnedAt == null;
    }

    public boolean isCreditConsumed() {
        return hasActiveCredit() && creditConsumedAt != null;
    }

    public void changeStatus(AttendanceStatus status, BigDecimal paymentAmount, Instant now) {
        AttendanceStatus previousStatus = this.status;
        this.status = status;
        if (status == AttendanceStatus.GOING) {
            paymentDeadlineRemovedAt = null;
        }
        if (paymentAmount == null || previousStatus == status) {
            return;
        }

        if (status == AttendanceStatus.NOT_GOING) {
            if (paymentStatus == PaymentStatus.PENDING) {
                paymentStatus = PaymentStatus.CANCELLED;
            } else if (paymentStatus == PaymentStatus.REPORTED) {
                requestSettlement(PaymentSettlementStatus.REVIEW_REQUIRED, now);
            } else if (paymentStatus == PaymentStatus.PAID
                    && (cashPaidAmount.signum() > 0 || isCreditConsumed())) {
                requestSettlement(PaymentSettlementStatus.PENDING, now);
            } else if (paymentStatus == PaymentStatus.PAID) {
                paymentStatus = PaymentStatus.CANCELLED;
            }
            return;
        }

        if (paymentSettlementStatus == PaymentSettlementStatus.NOT_RECEIVED
                || paymentSettlementStatus == PaymentSettlementStatus.REFUNDED
                || paymentSettlementStatus == PaymentSettlementStatus.CREDITED) {
            paymentStatus = PaymentStatus.PENDING;
            cashAmountDue = paymentAmount;
            cashPaidAmount = BigDecimal.ZERO;
        } else if (paymentSettlementStatus == PaymentSettlementStatus.RETAINED) {
            paymentStatus = PaymentStatus.PAID;
            cashAmountDue = paymentAmount;
            cashPaidAmount = paymentAmount;
        } else if (paymentStatus == null || paymentStatus == PaymentStatus.CANCELLED) {
            paymentStatus = PaymentStatus.PENDING;
            cashAmountDue = paymentAmount;
            cashPaidAmount = BigDecimal.ZERO;
        }
        if (paymentSettlementStatus != null) {
            paymentSettlementStatus = null;
            paymentSettlementRequestedAt = null;
            paymentSettlementResolvedAt = null;
        }
    }

    public void expireUnconfirmedCredit(BigDecimal paymentAmount, Instant now) {
        if (status != AttendanceStatus.PENDING) {
            return;
        }
        changeStatus(AttendanceStatus.NOT_GOING, paymentAmount, now);
    }

    public void removeForMissedPayment(BigDecimal paymentAmount, Instant now) {
        if (status != AttendanceStatus.GOING || paymentStatus != PaymentStatus.PENDING) {
            return;
        }
        changeStatus(AttendanceStatus.NOT_GOING, paymentAmount, now);
        paymentDeadlineRemovedAt = now;
    }

    public void reportPayment(Instant now) {
        if (paymentStatus == PaymentStatus.PENDING && cashAmountDue.signum() > 0) {
            paymentStatus = PaymentStatus.REPORTED;
            paymentReportedAt = now;
        }
    }

    public void confirmPayment(Instant now) {
        paymentStatus = PaymentStatus.PAID;
        cashPaidAmount = cashAmountDue;
        paymentConfirmedAt = now;
    }

    public void resolveSettlement(
            PaymentSettlementResolution resolution,
            BigDecimal settlementAmount,
            Instant now) {
        boolean reviewRequired = paymentSettlementStatus == PaymentSettlementStatus.REVIEW_REQUIRED;
        boolean settlementPending = paymentSettlementStatus == PaymentSettlementStatus.PENDING;
        if (!reviewRequired && !settlementPending) {
            throw new IllegalStateException("Payment settlement is not open");
        }
        if (!reviewRequired && resolution == PaymentSettlementResolution.NOT_RECEIVED) {
            throw new IllegalArgumentException("A confirmed payment cannot be marked as not received");
        }

        if (resolution == PaymentSettlementResolution.NOT_RECEIVED) {
            paymentStatus = PaymentStatus.CANCELLED;
            cashPaidAmount = BigDecimal.ZERO;
        } else {
            paymentStatus = PaymentStatus.PAID;
            if (cashPaidAmount.signum() == 0) {
                cashPaidAmount = settlementAmount;
            }
            if (paymentConfirmedAt == null) {
                paymentConfirmedAt = now;
            }
        }
        paymentSettlementStatus = switch (resolution) {
            case NOT_RECEIVED -> PaymentSettlementStatus.NOT_RECEIVED;
            case REFUNDED -> PaymentSettlementStatus.REFUNDED;
            case CREDITED -> PaymentSettlementStatus.CREDITED;
            case RETAINED -> PaymentSettlementStatus.RETAINED;
        };
        paymentSettlementResolvedAt = now;
    }

    public void reserveCredit(BigDecimal amount, BigDecimal paymentAmount, Instant now) {
        if (amount == null || amount.signum() <= 0 || paymentAmount == null) {
            throw new IllegalArgumentException("Credit reservation must be positive");
        }
        creditAppliedAmount = amount.min(paymentAmount);
        creditConsumedAt = null;
        creditReturnedAt = null;
        cashAmountDue = paymentAmount.subtract(creditAppliedAmount);
        cashPaidAmount = BigDecimal.ZERO;
        paymentStatus = cashAmountDue.signum() == 0
                ? PaymentStatus.PAID
                : PaymentStatus.PENDING;
        paymentReportedAt = null;
        paymentConfirmedAt = cashAmountDue.signum() == 0 ? now : null;
    }

    public void consumeCredit(Instant now) {
        if (!hasActiveCredit()) {
            throw new IllegalStateException("Credit reservation is not active");
        }
        creditConsumedAt = now;
    }

    public void releaseCredit(Instant now) {
        if (!hasActiveCredit()) {
            return;
        }
        creditReturnedAt = now;
        if (cashPaidAmount.signum() > 0) {
            paymentStatus = PaymentStatus.PAID;
        } else if (paymentStatus != PaymentStatus.REPORTED) {
            paymentStatus = PaymentStatus.PENDING;
        }
    }

    public void prepareCancellationSettlement(Instant now) {
        if (paymentSettlementStatus != null) {
            return;
        }
        if (paymentStatus == PaymentStatus.REPORTED) {
            requestSettlement(PaymentSettlementStatus.REVIEW_REQUIRED, now);
        } else if (cashPaidAmount.signum() > 0) {
            requestSettlement(PaymentSettlementStatus.PENDING, now);
        } else if (paymentStatus != null) {
            paymentStatus = PaymentStatus.CANCELLED;
        }
    }

    public void markAutomaticCreditReturn(Instant now) {
        paymentSettlementStatus = PaymentSettlementStatus.CREDITED;
        paymentSettlementRequestedAt = now;
        paymentSettlementResolvedAt = now;
    }

    public void requireReplacement(Instant now) {
        replacementRequiredAt = now;
        replacementUserId = null;
        replacementFilledAt = null;
    }

    public void fillReplacement(UUID userId, Instant now) {
        if (!isAwaitingReplacement()) {
            throw new IllegalStateException("Attendance is not awaiting a replacement");
        }
        replacementUserId = userId;
        replacementFilledAt = now;
    }

    public void markAddedAsReplacement(UUID departedUserId, Instant now) {
        addedAsReplacementAt = now;
        replacementForUserId = departedUserId;
    }

    public void reinstateByAdministrator(BigDecimal paymentAmount, Instant now) {
        changeStatus(AttendanceStatus.GOING, paymentAmount, now);
        replacementRequiredAt = null;
        replacementUserId = null;
        replacementFilledAt = null;
        addedAsReplacementAt = now;
        replacementForUserId = null;
    }

    public void closeRetainedCredit(Instant now) {
        if (hasActiveCredit()) {
            creditReturnedAt = now;
        }
    }

    public BigDecimal settlementAmount() {
        return cashPaidAmount.signum() > 0 ? cashPaidAmount : cashAmountDue;
    }

    private void requestSettlement(PaymentSettlementStatus status, Instant now) {
        paymentSettlementStatus = status;
        paymentSettlementRequestedAt = now;
        paymentSettlementResolvedAt = null;
    }
}
