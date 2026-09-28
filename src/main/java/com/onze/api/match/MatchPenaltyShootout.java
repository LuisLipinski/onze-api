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

@Entity
@Table(name = "match_penalty_shootouts")
public class MatchPenaltyShootout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false, unique = true)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PenaltyShootoutStatus status;

    @Column(name = "winner_team_number")
    private Integer winnerTeamNumber;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MatchPenaltyShootout() { }

    public MatchPenaltyShootout(UUID matchId) {
        this.matchId = matchId;
        this.status = PenaltyShootoutStatus.SETUP;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public PenaltyShootoutStatus getStatus() { return status; }
    public Integer getWinnerTeamNumber() { return winnerTeamNumber; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void start(Instant now) {
        if (status != PenaltyShootoutStatus.SETUP) throw new IllegalStateException("Shootout cannot start");
        status = PenaltyShootoutStatus.IN_PROGRESS;
        startedAt = now;
    }

    public void decide(int teamNumber, Instant now) {
        if (status != PenaltyShootoutStatus.IN_PROGRESS) throw new IllegalStateException("Shootout cannot be decided");
        status = PenaltyShootoutStatus.AWAITING_CONFIRMATION;
        winnerTeamNumber = teamNumber;
        decidedAt = now;
    }

    public void complete(Instant now) {
        if (status != PenaltyShootoutStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("Shootout cannot be completed");
        }
        status = PenaltyShootoutStatus.COMPLETED;
        completedAt = now;
    }
}
