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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "group_team_identities", uniqueConstraints = @UniqueConstraint(
        name = "uk_group_team_identities_group_type_team",
        columnNames = {"group_id", "match_type", "team_number"}))
public class GroupTeamIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private MatchType matchType;

    @Column(name = "team_number", nullable = false)
    private int teamNumber;

    @Column(name = "team_name", nullable = false, length = 80)
    private String teamName;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GroupTeamIdentity() { }

    public GroupTeamIdentity(
            UUID groupId,
            MatchType matchType,
            int teamNumber,
            String teamName,
            String imageUrl) {
        this.groupId = groupId;
        this.matchType = matchType;
        this.teamNumber = teamNumber;
        this.teamName = teamName;
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
    public UUID getGroupId() { return groupId; }
    public MatchType getMatchType() { return matchType; }
    public int getTeamNumber() { return teamNumber; }
    public String getTeamName() { return teamName; }
    public String getImageUrl() { return imageUrl; }

    public void updateName(String newName) { teamName = newName; }
    public void updateImageUrl(String newImageUrl) { imageUrl = newImageUrl; }
}
