package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "match_team_generation_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_team_generation_history_signature",
        columnNames = {"match_id", "normalized_signature"}))
public class MatchTeamGenerationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "normalized_signature", nullable = false, length = 4096)
    private String normalizedSignature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchTeamGenerationHistory() {
    }

    public MatchTeamGenerationHistory(UUID matchId, String normalizedSignature) {
        this.matchId = matchId;
        this.normalizedSignature = normalizedSignature;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public String getNormalizedSignature() { return normalizedSignature; }
    public Instant getCreatedAt() { return createdAt; }
}
