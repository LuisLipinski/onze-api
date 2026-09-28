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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "match_periods", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_periods_type_number",
        columnNames = {"match_id", "period_type", "period_number"}))
public class MatchPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 16)
    private MatchPeriodType periodType;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "added_time_minutes")
    private Integer addedTimeMinutes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected MatchPeriod() { }

    public MatchPeriod(UUID matchId, MatchPeriodType periodType, int periodNumber,
            int durationMinutes, Instant startedAt) {
        this.matchId = matchId;
        this.periodType = periodType;
        this.periodNumber = periodNumber;
        this.durationMinutes = durationMinutes;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public MatchPeriodType getPeriodType() { return periodType; }
    public int getPeriodNumber() { return periodNumber; }
    public int getDurationMinutes() { return durationMinutes; }
    public Integer getAddedTimeMinutes() { return addedTimeMinutes; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isRunning() { return endedAt == null; }

    public void configureAddedTime(int minutes) {
        if (!isRunning()) throw new IllegalStateException("Period already ended");
        addedTimeMinutes = minutes;
    }

    public void finish(Instant now) {
        if (!isRunning()) throw new IllegalStateException("Period already ended");
        endedAt = now;
    }
}
