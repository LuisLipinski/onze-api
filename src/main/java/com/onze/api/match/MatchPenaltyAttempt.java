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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "match_penalty_attempts", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_penalty_attempts_sequence",
        columnNames = {"match_id", "sequence_number"}))
public class MatchPenaltyAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name = "team_number", nullable = false) private int teamNumber;
    @Column(name = "assignment_id") private UUID assignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", length = 32) private TeamParticipantType participantType;
    @Column(name = "participant_id") private UUID participantId;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(nullable = false) private boolean scored;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MatchPenaltyAttempt() { }

    public MatchPenaltyAttempt(UUID matchId, int sequenceNumber, int roundNumber, int teamNumber,
            MatchTeamAssignment assignment, String displayName, boolean scored,
            UUID createdByUserId, Instant createdAt) {
        this.matchId = matchId;
        this.sequenceNumber = sequenceNumber;
        this.roundNumber = roundNumber;
        this.teamNumber = teamNumber;
        if (assignment != null) {
            assignmentId = assignment.getId();
            participantType = assignment.getParticipantType();
            participantId = assignment.getParticipantId();
        }
        this.displayName = displayName;
        this.scored = scored;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getRoundNumber() { return roundNumber; }
    public int getTeamNumber() { return teamNumber; }
    public UUID getAssignmentId() { return assignmentId; }
    public TeamParticipantType getParticipantType() { return participantType; }
    public UUID getParticipantId() { return participantId; }
    public String getDisplayName() { return displayName; }
    public boolean isScored() { return scored; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
