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

@Entity
@Table(name = "match_series")
public class MatchSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 255)
    private String venue;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchSeries() {
    }

    public MatchSeries(
            UUID groupId,
            UUID createdBy,
            String timeZone,
            String venue,
            int maxPlayers,
            String notes) {
        this.groupId = groupId;
        this.createdBy = createdBy;
        this.timeZone = timeZone;
        this.venue = venue;
        this.maxPlayers = maxPlayers;
        this.notes = notes;
        this.active = true;
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

    public UUID getCreatedBy() {
        return createdBy;
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

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }
}
