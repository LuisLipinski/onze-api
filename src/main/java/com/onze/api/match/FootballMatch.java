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
        name = "football_matches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_football_matches_series_occurrence",
                columnNames = {"series_id", "occurrence_number"}))
public class FootballMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "series_id")
    private UUID seriesId;

    @Column(name = "occurrence_number")
    private Integer occurrenceNumber;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 255)
    private String venue;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "payment_amount", precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "pix_key", length = 255)
    private String pixKey;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MatchStatus status;

    @Column(name = "attendance_opens_at", nullable = false)
    private Instant attendanceOpensAt;

    @Column(name = "attendance_opened_at")
    private Instant attendanceOpenedAt;

    @Column(name = "signup_deadline", nullable = false)
    private Instant signupDeadline;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FootballMatch() {
    }

    public FootballMatch(
            UUID groupId,
            UUID seriesId,
            Integer occurrenceNumber,
            Instant startsAt,
            String timeZone,
            String venue,
            int maxPlayers,
            BigDecimal paymentAmount,
            String pixKey,
            String notes,
            Instant attendanceOpensAt,
            Instant attendanceOpenedAt,
            Instant signupDeadline,
            Instant paymentDeadline,
            UUID createdBy) {
        this.groupId = groupId;
        this.seriesId = seriesId;
        this.occurrenceNumber = occurrenceNumber;
        this.startsAt = startsAt;
        this.timeZone = timeZone;
        this.venue = venue;
        this.maxPlayers = maxPlayers;
        this.paymentAmount = paymentAmount;
        this.pixKey = pixKey;
        this.notes = notes;
        this.status = MatchStatus.SCHEDULED;
        this.attendanceOpensAt = attendanceOpensAt;
        this.attendanceOpenedAt = attendanceOpenedAt;
        this.signupDeadline = signupDeadline;
        this.paymentDeadline = paymentDeadline;
        this.createdBy = createdBy;
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

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getSeriesId() {
        return seriesId;
    }

    public Integer getOccurrenceNumber() {
        return occurrenceNumber;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getVenue() {
        return venue;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public String getPixKey() {
        return pixKey;
    }

    public boolean isPaymentRequired() {
        return paymentAmount != null && pixKey != null;
    }

    public String getNotes() {
        return notes;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Instant getAttendanceOpensAt() {
        return attendanceOpensAt;
    }

    public Instant getAttendanceOpenedAt() {
        return attendanceOpenedAt;
    }

    public Instant getSignupDeadline() {
        return signupDeadline;
    }

    public Instant getPaymentDeadline() {
        return paymentDeadline;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isAttendanceOpen(Instant now) {
        return status == MatchStatus.SCHEDULED
                && attendanceOpenedAt != null
                && startsAt.isAfter(now);
    }

    public boolean isSignupOpen(Instant now) {
        return isAttendanceOpen(now)
                && signupDeadline != null
                && !now.isAfter(signupDeadline);
    }

    public boolean isPaymentOpen(Instant now) {
        return isPaymentRequired()
                && isAttendanceOpen(now)
                && paymentDeadline != null
                && !now.isAfter(paymentDeadline);
    }

    public void openAttendance(Instant openedAt) {
        if (status == MatchStatus.SCHEDULED && attendanceOpenedAt == null) {
            attendanceOpenedAt = openedAt;
        }
    }

    public void cancel() {
        status = MatchStatus.CANCELLED;
    }
}
