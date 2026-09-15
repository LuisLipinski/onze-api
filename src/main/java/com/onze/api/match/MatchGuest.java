package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import com.onze.api.group.PlayerPosition;

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

@Entity
@Table(name = "match_guests")
public class MatchGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_position", nullable = false, length = 32)
    private PlayerPosition primaryPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_position", length = 32)
    private PlayerPosition secondaryPosition;

    @Column(name = "technical_profile_updated_at")
    private Instant technicalProfileUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchGuest() {
    }

    public MatchGuest(
            UUID matchId,
            String displayName,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition) {
        this.matchId = matchId;
        this.displayName = displayName;
        this.primaryPosition = primaryPosition;
        this.secondaryPosition = secondaryPosition;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public String getDisplayName() { return displayName; }
    public PlayerPosition getPrimaryPosition() { return primaryPosition; }
    public PlayerPosition getSecondaryPosition() { return secondaryPosition; }
    public Instant getTechnicalProfileUpdatedAt() { return technicalProfileUpdatedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markTechnicalProfileUpdated(Instant updatedAt) {
        technicalProfileUpdatedAt = updatedAt;
    }
}
