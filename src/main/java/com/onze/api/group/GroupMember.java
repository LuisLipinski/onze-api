package com.onze.api.group;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_members_group_user",
                columnNames = {"group_id", "user_id"}))
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GroupRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "group_admin_permissions",
            joinColumns = @JoinColumn(name = "group_member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 48)
    private Set<GroupAdminPermission> permissions = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_position", length = 32)
    private PlayerPosition primaryPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_position", length = 32)
    private PlayerPosition secondaryPosition;

    @Column(name = "can_play_goalkeeper", nullable = false)
    private boolean canPlayGoalkeeper;

    @Enumerated(EnumType.STRING)
    @Column(name = "dominant_foot", length = 16)
    private DominantFoot dominantFoot;

    @Column(name = "technical_level")
    private Integer technicalLevel;

    @Column(name = "technical_profile_updated_at")
    private Instant technicalProfileUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GroupMember() {
    }

    public GroupMember(UUID groupId, UUID userId, GroupRole role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public GroupRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PlayerPosition getPrimaryPosition() {
        return primaryPosition;
    }

    public PlayerPosition getSecondaryPosition() {
        return secondaryPosition;
    }

    public List<PlayerPosition> getPositions() {
        return secondaryPosition == null
                ? primaryPosition == null ? List.of() : List.of(primaryPosition)
                : List.of(primaryPosition, secondaryPosition);
    }

    public boolean canPlayGoalkeeper() {
        return canPlayGoalkeeper;
    }

    public DominantFoot getDominantFoot() {
        return dominantFoot;
    }

    public Integer getTechnicalLevel() {
        return technicalLevel;
    }

    public Instant getTechnicalProfileUpdatedAt() {
        return technicalProfileUpdatedAt;
    }

    public void markTechnicalProfileUpdated(Instant updatedAt) {
        technicalProfileUpdatedAt = updatedAt;
    }

    public boolean isSportsProfileComplete() {
        return primaryPosition != null && dominantFoot != null;
    }

    public boolean canPlayPosition(PlayerPosition targetPosition) {
        return PlayerPosition.canPlayPosition(primaryPosition, targetPosition)
                || PlayerPosition.canPlayPosition(secondaryPosition, targetPosition);
    }

    public Set<GroupAdminPermission> getPermissions() {
        if (role == GroupRole.PRIMARY_ADMIN) {
            return Set.of(GroupAdminPermission.values());
        }
        if (role != GroupRole.ADMIN) {
            return Set.of();
        }
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(GroupAdminPermission permission) {
        return role == GroupRole.PRIMARY_ADMIN
                || (role == GroupRole.ADMIN && permissions.contains(permission));
    }

    public void replacePermissions(Collection<GroupAdminPermission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    public void changeRole(GroupRole role) {
        this.role = role;
        if (role != GroupRole.ADMIN) {
            permissions.clear();
        }
    }

    public void updateOwnSportsProfile(
            PlayerPosition newPrimaryPosition,
            PlayerPosition newSecondaryPosition,
            boolean newCanPlayGoalkeeper,
            DominantFoot newDominantFoot) {
        primaryPosition = newPrimaryPosition;
        secondaryPosition = newSecondaryPosition;
        canPlayGoalkeeper = newPrimaryPosition != PlayerPosition.GOALKEEPER
                && newSecondaryPosition != PlayerPosition.GOALKEEPER
                && newCanPlayGoalkeeper;
        dominantFoot = newDominantFoot;
    }

    public void updateSportsProfile(
            PlayerPosition newPrimaryPosition,
            PlayerPosition newSecondaryPosition,
            boolean newCanPlayGoalkeeper,
            DominantFoot newDominantFoot,
            Integer newTechnicalLevel) {
        updateOwnSportsProfile(
                newPrimaryPosition,
                newSecondaryPosition,
                newCanPlayGoalkeeper,
                newDominantFoot);
        technicalLevel = newTechnicalLevel;
    }
}
