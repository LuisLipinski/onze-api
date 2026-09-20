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
@Table(name = "match_goal_events")
public class MatchGoalEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "side_number", nullable = false)
    private int sideNumber;

    @Column(name = "scorer_assignment_id", nullable = false)
    private UUID scorerAssignmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scorer_participant_type", nullable = false, length = 32)
    private TeamParticipantType scorerParticipantType;

    @Column(name = "scorer_participant_id", nullable = false)
    private UUID scorerParticipantId;

    @Column(name = "scorer_display_name", length = 120)
    private String scorerDisplayName;

    @Column(name = "assist_assignment_id")
    private UUID assistAssignmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assist_participant_type", length = 32)
    private TeamParticipantType assistParticipantType;

    @Column(name = "assist_participant_id")
    private UUID assistParticipantId;

    @Column(name = "assist_display_name", length = 120)
    private String assistDisplayName;

    @Column(name = "penalty", nullable = false)
    private boolean penalty;

    @Column(name = "elapsed_seconds", nullable = false)
    private long elapsedSeconds;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MatchGoalEvent() { }

    public MatchGoalEvent(UUID matchId, MatchTeamAssignment scorer, String scorerDisplayName,
            MatchTeamAssignment assist, String assistDisplayName,
            boolean penalty, long elapsedSeconds, UUID createdByUserId, Instant createdAt) {
        this.matchId = matchId;
        this.sideNumber = scorer.getTeamNumber();
        this.scorerAssignmentId = scorer.getId();
        this.scorerParticipantType = scorer.getParticipantType();
        this.scorerParticipantId = scorer.getParticipantId();
        this.scorerDisplayName = scorerDisplayName;
        if (assist != null) {
            this.assistAssignmentId = assist.getId();
            this.assistParticipantType = assist.getParticipantType();
            this.assistParticipantId = assist.getParticipantId();
            this.assistDisplayName = assistDisplayName;
        }
        this.penalty = penalty;
        this.elapsedSeconds = elapsedSeconds;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getSideNumber() { return sideNumber; }
    public UUID getScorerAssignmentId() { return scorerAssignmentId; }
    public TeamParticipantType getScorerParticipantType() { return scorerParticipantType; }
    public UUID getScorerParticipantId() { return scorerParticipantId; }
    public String getScorerDisplayName() { return scorerDisplayName; }
    public UUID getAssistAssignmentId() { return assistAssignmentId; }
    public TeamParticipantType getAssistParticipantType() { return assistParticipantType; }
    public UUID getAssistParticipantId() { return assistParticipantId; }
    public String getAssistDisplayName() { return assistDisplayName; }
    public boolean isPenalty() { return penalty; }
    public long getElapsedSeconds() { return elapsedSeconds; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
