package com.onze.api.match;

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
@Table(name = "match_penalty_takers", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_penalty_takers_order",
        columnNames = {"match_id", "team_number", "kick_order"}))
public class MatchPenaltyTaker {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "team_number", nullable = false) private int teamNumber;
    @Column(name = "kick_order", nullable = false) private int kickOrder;
    @Column(name = "assignment_id") private UUID assignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", length = 32) private TeamParticipantType participantType;
    @Column(name = "participant_id") private UUID participantId;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;

    protected MatchPenaltyTaker() { }

    public MatchPenaltyTaker(UUID matchId, int teamNumber, int kickOrder,
            MatchTeamAssignment assignment, String displayName) {
        this.matchId = matchId;
        this.teamNumber = teamNumber;
        this.kickOrder = kickOrder;
        if (assignment != null) {
            assignmentId = assignment.getId();
            participantType = assignment.getParticipantType();
            participantId = assignment.getParticipantId();
        }
        this.displayName = displayName;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getTeamNumber() { return teamNumber; }
    public int getKickOrder() { return kickOrder; }
    public UUID getAssignmentId() { return assignmentId; }
    public TeamParticipantType getParticipantType() { return participantType; }
    public UUID getParticipantId() { return participantId; }
    public String getDisplayName() { return displayName; }
}
