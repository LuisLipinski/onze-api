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
@Table(name = "match_team_images", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_team_images_match_team", columnNames = {"match_id", "team_number"}))
public class MatchTeamImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "team_number", nullable = false)
    private int teamNumber;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchTeamImage() { }

    public MatchTeamImage(UUID matchId, int teamNumber, String imageUrl) {
        this.matchId = matchId;
        this.teamNumber = teamNumber;
        this.imageUrl = imageUrl;
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
    public int getTeamNumber() { return teamNumber; }
    public String getImageUrl() { return imageUrl; }

    public void updateImageUrl(String newImageUrl) { imageUrl = newImageUrl; }
}
