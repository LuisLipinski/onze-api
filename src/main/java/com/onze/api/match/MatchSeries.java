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

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private MatchType matchType;

    @Column(name = "team_count")
    private Integer teamCount;

    @Column(name = "required_goalkeepers", nullable = false)
    private int requiredGoalkeepers;

    @Column(name = "payment_amount", precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "pix_key", length = 255)
    private String pixKey;

    @Column(name = "goalkeeper_pays", nullable = false)
    private boolean goalkeeperPays;

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
            MatchType matchType,
            Integer teamCount,
            int requiredGoalkeepers,
            BigDecimal paymentAmount,
            String pixKey,
            boolean goalkeeperPays,
            String notes) {
        this.groupId = groupId;
        this.createdBy = createdBy;
        this.timeZone = timeZone;
        this.venue = venue;
        this.maxPlayers = maxPlayers;
        this.matchType = matchType;
        this.teamCount = teamCount;
        this.requiredGoalkeepers = requiredGoalkeepers;
        this.paymentAmount = paymentAmount;
        this.pixKey = pixKey;
        this.goalkeeperPays = goalkeeperPays;
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

    public MatchType getMatchType() {
        return matchType;
    }

    public Integer getTeamCount() {
        return teamCount;
    }

    public int getRequiredGoalkeepers() {
        return requiredGoalkeepers;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public String getPixKey() {
        return pixKey;
    }

    public boolean isGoalkeeperPays() {
        return goalkeeperPays;
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
