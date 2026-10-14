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
import jakarta.persistence.Table;

@Entity
@Table(name = "match_shot_events")
public class MatchShotEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "side_number", nullable = false) private int sideNumber;
    @Column(name = "player_assignment_id", nullable = false) private UUID playerAssignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "player_participant_type", nullable = false, length = 32)
    private TeamParticipantType playerParticipantType;
    @Column(name = "player_participant_id", nullable = false) private UUID playerParticipantId;
    @Column(name = "player_display_name", nullable = false, length = 120)
    private String playerDisplayName;
    @Column(name = "on_target", nullable = false) private boolean onTarget;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MatchShotEvent() { }

    public MatchShotEvent(UUID matchId, MatchTeamAssignment player, String name,
            boolean onTarget, Instant createdAt) {
        this.matchId = matchId;
        this.sideNumber = player.getTeamNumber();
        this.playerAssignmentId = player.getId();
        this.playerParticipantType = player.getParticipantType();
        this.playerParticipantId = player.getParticipantId();
        this.playerDisplayName = name;
        this.onTarget = onTarget;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getSideNumber() { return sideNumber; }
    public UUID getPlayerAssignmentId() { return playerAssignmentId; }
    public TeamParticipantType getPlayerParticipantType() { return playerParticipantType; }
    public UUID getPlayerParticipantId() { return playerParticipantId; }
    public String getPlayerDisplayName() { return playerDisplayName; }
    public boolean isOnTarget() { return onTarget; }
    public Instant getCreatedAt() { return createdAt; }
}
