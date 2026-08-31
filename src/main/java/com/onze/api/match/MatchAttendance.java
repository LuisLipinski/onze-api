package com.onze.api.match;

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
            boolean paymentRequired) {
        this.matchId = matchId;
        this.userId = userId;
        this.status = status;
        if (status == AttendanceStatus.GOING && paymentRequired) {
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

    public void changeStatus(AttendanceStatus status, boolean paymentRequired, Instant now) {
        AttendanceStatus previousStatus = this.status;
        this.status = status;
        if (!paymentRequired || previousStatus == status) {
            return;
        }

        if (status == AttendanceStatus.NOT_GOING) {
            if (paymentStatus == PaymentStatus.PENDING) {
                paymentStatus = PaymentStatus.CANCELLED;
            } else if (paymentStatus == PaymentStatus.REPORTED) {
                requestSettlement(PaymentSettlementStatus.REVIEW_REQUIRED, now);
            } else if (paymentStatus == PaymentStatus.PAID) {
                requestSettlement(PaymentSettlementStatus.PENDING, now);
            }
            return;
        }

        if (paymentSettlementStatus == PaymentSettlementStatus.NOT_RECEIVED
                || paymentSettlementStatus == PaymentSettlementStatus.REFUNDED) {
            paymentStatus = PaymentStatus.PENDING;
        } else if (paymentSettlementStatus == PaymentSettlementStatus.CREDITED
                || paymentSettlementStatus == PaymentSettlementStatus.RETAINED) {
            paymentStatus = PaymentStatus.PAID;
        } else if (paymentStatus == null || paymentStatus == PaymentStatus.CANCELLED) {
            paymentStatus = PaymentStatus.PENDING;
        }
        if (paymentSettlementStatus != null) {
            paymentSettlementStatus = null;
            paymentSettlementRequestedAt = null;
            paymentSettlementResolvedAt = null;
        }
    }

    public void reportPayment(Instant now) {
        if (paymentStatus == PaymentStatus.PENDING) {
            paymentStatus = PaymentStatus.REPORTED;
            paymentReportedAt = now;
        }
    }

    public void confirmPayment(Instant now) {
        paymentStatus = PaymentStatus.PAID;
        paymentConfirmedAt = now;
    }

    public void resolveSettlement(PaymentSettlementResolution resolution, Instant now) {
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
        } else {
            paymentStatus = PaymentStatus.PAID;
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

    private void requestSettlement(PaymentSettlementStatus status, Instant now) {
        paymentSettlementStatus = status;
        paymentSettlementRequestedAt = now;
        paymentSettlementResolvedAt = null;
    }
}
