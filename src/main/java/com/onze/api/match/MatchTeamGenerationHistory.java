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
        name = "uk_match_team_generation_history_number",
        columnNames = {"match_id", "generation_number"}))
public class MatchTeamGenerationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "generation_number", nullable = false)
    private int generationNumber;

    @Column(name = "division_signature", nullable = false, columnDefinition = "TEXT")
    private String divisionSignature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchTeamGenerationHistory() {
    }

    public MatchTeamGenerationHistory(
            UUID matchId,
            UUID groupId,
            int generationNumber,
            String divisionSignature) {
        this.matchId = matchId;
        this.groupId = groupId;
        this.generationNumber = generationNumber;
        this.divisionSignature = divisionSignature;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public UUID getGroupId() { return groupId; }
    public int getGenerationNumber() { return generationNumber; }
    public String getDivisionSignature() { return divisionSignature; }
    public Instant getCreatedAt() { return createdAt; }
}
