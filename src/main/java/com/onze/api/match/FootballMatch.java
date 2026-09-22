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

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private MatchType matchType;

    @Column(name = "team_count")
    private Integer teamCount;

    @Column(name = "required_goalkeepers", nullable = false)
    private int requiredGoalkeepers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MatchModality modality;

    @Column(name = "minimum_players", nullable = false)
    private int minimumPlayers;

    @Column(name = "payment_amount", precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "pix_key", length = 255)
    private String pixKey;

    @Column(name = "goalkeeper_pays", nullable = false)
    private boolean goalkeeperPays;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MatchStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "live_version", nullable = false)
    private long liveVersion;

    @Column(name = "attendance_opens_at", nullable = false)
    private Instant attendanceOpensAt;

    @Column(name = "attendance_opened_at")
    private Instant attendanceOpenedAt;

    @Column(name = "signup_deadline", nullable = false)
    private Instant signupDeadline;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "below_minimum_approved", nullable = false)
    private boolean belowMinimumApproved;

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
            MatchType matchType,
            Integer teamCount,
            int requiredGoalkeepers,
            MatchModality modality,
            int minimumPlayers,
            BigDecimal paymentAmount,
            String pixKey,
            boolean goalkeeperPays,
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
        this.matchType = matchType;
        this.teamCount = teamCount;
        this.requiredGoalkeepers = requiredGoalkeepers;
        this.modality = modality;
        this.minimumPlayers = minimumPlayers;
        this.paymentAmount = paymentAmount;
        this.pixKey = pixKey;
        this.goalkeeperPays = goalkeeperPays;
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

    public MatchType getMatchType() {
        return matchType;
    }

    public Integer getTeamCount() {
        return teamCount;
    }

    public int getRequiredGoalkeepers() {
        return requiredGoalkeepers;
    }

    public MatchModality getModality() {
        return modality;
    }

    public int getMinimumPlayers() {
        return minimumPlayers;
    }

    public int getIdealPlayers() {
        int sides = matchType == MatchType.INTERNAL ? teamCount : 1;
        return modality.playersPerTeam() * sides;
    }

    public void updatePlayerConfiguration(MatchModality newModality, int newMinimumPlayers) {
        modality = newModality;
        minimumPlayers = newMinimumPlayers;
        belowMinimumApproved = false;
    }

    public void updateMaximumPlayers(int newMaxPlayers) {
        maxPlayers = newMaxPlayers;
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

    public boolean isGoalkeeperPays() {
        return goalkeeperPays;
    }

    public String getNotes() {
        return notes;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() { return startedAt; }

    public Instant getFinishedAt() { return finishedAt; }

    public long getLiveVersion() { return liveVersion; }

    public void start(Instant now) {
        if (status != MatchStatus.SCHEDULED) throw new IllegalStateException("Match cannot be started");
        status = MatchStatus.IN_PROGRESS;
        startedAt = now;
        liveStateChanged();
    }

    public void finish(Instant now) {
        if (status != MatchStatus.IN_PROGRESS) throw new IllegalStateException("Match cannot be finished");
        status = MatchStatus.FINISHED;
        finishedAt = now;
        liveStateChanged();
    }

    public void resetLiveMatch() {
        if (status != MatchStatus.IN_PROGRESS) throw new IllegalStateException("Match cannot be reset");
        status = MatchStatus.SCHEDULED;
        startedAt = null;
        finishedAt = null;
        liveStateChanged();
    }

    public void liveStateChanged() {
        liveVersion++;
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

    public boolean isBelowMinimumApproved() {
        return belowMinimumApproved;
    }

    public void approveBelowMinimum() {
        belowMinimumApproved = true;
    }

    public void extendSignupDeadline(Instant newSignupDeadline, Instant newPaymentDeadline) {
        signupDeadline = newSignupDeadline;
        paymentDeadline = newPaymentDeadline;
        belowMinimumApproved = false;
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
