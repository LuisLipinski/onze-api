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
@Table(name = "match_team_assignments", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_team_assignments_participant",
        columnNames = {"match_id", "participant_type", "participant_id"}))
public class MatchTeamAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "team_number", nullable = false)
    private int teamNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 32)
    private TeamParticipantType participantType;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "assigned_role", nullable = false, length = 48)
    private String assignedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_reason", nullable = false, length = 48)
    private TeamAssignmentReason assignmentReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_origin", nullable = false, length = 24)
    private TeamPositionOrigin positionOrigin;

    @Column(name = "manually_changed", nullable = false)
    private boolean manuallyChanged;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchTeamAssignment() {
    }

    public MatchTeamAssignment(
            UUID matchId,
            int teamNumber,
            TeamParticipantType participantType,
            UUID participantId,
            String assignedRole,
            TeamAssignmentReason assignmentReason,
            TeamPositionOrigin positionOrigin) {
        this.matchId = matchId;
        this.teamNumber = teamNumber;
        this.participantType = participantType;
        this.participantId = participantId;
        this.assignedRole = assignedRole;
        this.assignmentReason = assignmentReason;
        this.positionOrigin = positionOrigin;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getTeamNumber() { return teamNumber; }
    public TeamParticipantType getParticipantType() { return participantType; }
    public UUID getParticipantId() { return participantId; }
    public String getAssignedRole() { return assignedRole; }
    public TeamAssignmentReason getAssignmentReason() { return assignmentReason; }
    public TeamPositionOrigin getPositionOrigin() { return positionOrigin; }
    public boolean isManuallyChanged() { return manuallyChanged; }

    void rebalanceToTeam(int newTeamNumber) {
        teamNumber = newTeamNumber;
    }

    public void changeByAdministrator(int newTeamNumber, String newRole) {
        teamNumber = newTeamNumber;
        assignedRole = newRole;
        assignmentReason = TeamAssignmentReason.MANUAL_ADMIN_CHANGE;
        positionOrigin = TeamPositionOrigin.MANUAL;
        manuallyChanged = true;
    }
}
