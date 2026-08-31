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

    public void changeStatus(AttendanceStatus status, boolean paymentRequired) {
        this.status = status;
        if (status == AttendanceStatus.GOING && paymentRequired && paymentStatus == null) {
            paymentStatus = PaymentStatus.PENDING;
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
}
