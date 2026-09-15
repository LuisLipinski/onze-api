package com.onze.api.technical;

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
@Table(name = "group_member_skill_ratings", uniqueConstraints = @UniqueConstraint(
        name = "uk_group_member_skill_ratings_member_skill",
        columnNames = {"group_member_id", "skill"}))
public class GroupMemberSkillRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_member_id", nullable = false)
    private UUID groupMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private PlayerSkill skill;

    @Column(nullable = false)
    private int rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GroupMemberSkillRating() {
    }

    public GroupMemberSkillRating(UUID groupMemberId, PlayerSkill skill, int rating) {
        this.groupMemberId = groupMemberId;
        this.skill = skill;
        this.rating = rating;
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

    public UUID getGroupMemberId() { return groupMemberId; }
    public PlayerSkill getSkill() { return skill; }
    public int getRating() { return rating; }
}
