package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "live_match_scores", uniqueConstraints = @UniqueConstraint(
        name = "uk_live_match_scores_match_side", columnNames = {"match_id", "side_number"}))
public class LiveMatchScore {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "side_number", nullable = false)
    private int sideNumber;

    @Column(nullable = false)
    private int score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LiveMatchScore() { }

    public LiveMatchScore(UUID matchId, int sideNumber) {
        this.matchId = matchId;
        this.sideNumber = sideNumber;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getSideNumber() { return sideNumber; }
    public int getScore() { return score; }

    public void update(int newScore) { score = newScore; }
}
