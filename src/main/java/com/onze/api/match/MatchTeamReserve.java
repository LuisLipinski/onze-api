package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "match_team_reserves")
public class MatchTeamReserve {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchTeamReserve() {
    }

    public MatchTeamReserve(UUID assignmentId, UUID matchId) {
        this.assignmentId = assignmentId;
        this.matchId = matchId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getAssignmentId() { return assignmentId; }
    public UUID getMatchId() { return matchId; }
    public Instant getCreatedAt() { return createdAt; }
}
