package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import com.onze.api.technical.PlayerSkill;

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
@Table(name = "match_guest_skill_ratings", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_guest_skill_ratings_guest_skill",
        columnNames = {"guest_id", "skill"}))
public class MatchGuestSkillRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private PlayerSkill skill;

    @Column(nullable = false)
    private int rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchGuestSkillRating() {
    }

    public MatchGuestSkillRating(UUID guestId, PlayerSkill skill, int rating) {
        this.guestId = guestId;
        this.skill = skill;
        this.rating = rating;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getGuestId() { return guestId; }
    public PlayerSkill getSkill() { return skill; }
    public int getRating() { return rating; }
}
