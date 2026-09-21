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
@Table(name = "match_card_events")
public class MatchCardEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "side_number", nullable = false) private int sideNumber;
    @Column(name = "player_assignment_id", nullable = false) private UUID playerAssignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "player_participant_type", nullable = false, length = 32)
    private TeamParticipantType playerParticipantType;
    @Column(name = "player_participant_id", nullable = false) private UUID playerParticipantId;
    @Column(name = "player_display_name", nullable = false, length = 120) private String playerDisplayName;
    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 16) private MatchCardType cardType;
    @Column(name = "elapsed_seconds", nullable = false) private long elapsedSeconds;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MatchCardEvent() { }

    public MatchCardEvent(UUID matchId, MatchTeamAssignment player, String playerDisplayName,
            MatchCardType cardType, long elapsedSeconds, UUID createdByUserId, Instant createdAt) {
        this.matchId = matchId;
        this.sideNumber = player.getTeamNumber();
        this.playerAssignmentId = player.getId();
        this.playerParticipantType = player.getParticipantType();
        this.playerParticipantId = player.getParticipantId();
        this.playerDisplayName = playerDisplayName;
        this.cardType = cardType;
        this.elapsedSeconds = elapsedSeconds;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getSideNumber() { return sideNumber; }
    public UUID getPlayerAssignmentId() { return playerAssignmentId; }
    public TeamParticipantType getPlayerParticipantType() { return playerParticipantType; }
    public UUID getPlayerParticipantId() { return playerParticipantId; }
    public String getPlayerDisplayName() { return playerDisplayName; }
    public MatchCardType getCardType() { return cardType; }
    public long getElapsedSeconds() { return elapsedSeconds; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
