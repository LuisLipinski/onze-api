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
@Table(name = "match_foul_events")
public class MatchFoulEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "offender_side_number", nullable = false) private int offenderSideNumber;
    @Column(name = "offender_assignment_id", nullable = false) private UUID offenderAssignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "offender_participant_type", nullable = false, length = 32)
    private TeamParticipantType offenderParticipantType;
    @Column(name = "offender_participant_id", nullable = false) private UUID offenderParticipantId;
    @Column(name = "offender_display_name", nullable = false, length = 120)
    private String offenderDisplayName;
    @Column(name = "victim_side_number") private Integer victimSideNumber;
    @Column(name = "victim_assignment_id") private UUID victimAssignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "victim_participant_type", length = 32) private TeamParticipantType victimParticipantType;
    @Column(name = "victim_participant_id") private UUID victimParticipantId;
    @Column(name = "victim_display_name", length = 120) private String victimDisplayName;
    @Column(nullable = false) private boolean penalty;
    @Column(name = "taker_assignment_id") private UUID takerAssignmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "taker_participant_type", length = 32) private TeamParticipantType takerParticipantType;
    @Column(name = "taker_participant_id") private UUID takerParticipantId;
    @Column(name = "taker_display_name", length = 120) private String takerDisplayName;
    @Column private Boolean scored;
    @Column(name = "goal_event_id") private UUID goalEventId;
    @Column(name = "shot_event_id") private UUID shotEventId;
    @Column(name = "elapsed_seconds", nullable = false) private long elapsedSeconds;
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 16) private MatchPeriodType periodType;
    @Column(name = "period_number") private Integer periodNumber;
    @Column(name = "period_elapsed_seconds") private Long periodElapsedSeconds;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolution_elapsed_seconds") private Long resolutionElapsedSeconds;
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_period_type", length = 16) private MatchPeriodType resolutionPeriodType;
    @Column(name = "resolution_period_number") private Integer resolutionPeriodNumber;
    @Column(name = "resolution_period_elapsed_seconds") private Long resolutionPeriodElapsedSeconds;

    protected MatchFoulEvent() { }

    public MatchFoulEvent(UUID matchId, MatchTeamAssignment offender, String offenderName,
            MatchTeamAssignment victim, String victimName, boolean penalty,
            long elapsedSeconds, MatchPeriodType periodType, Integer periodNumber,
            Long periodElapsedSeconds, UUID createdByUserId, Instant createdAt) {
        this.matchId = matchId;
        this.offenderSideNumber = offender.getTeamNumber();
        this.offenderAssignmentId = offender.getId();
        this.offenderParticipantType = offender.getParticipantType();
        this.offenderParticipantId = offender.getParticipantId();
        this.offenderDisplayName = offenderName;
        if (victim != null) {
            this.victimSideNumber = victim.getTeamNumber();
            this.victimAssignmentId = victim.getId();
            this.victimParticipantType = victim.getParticipantType();
            this.victimParticipantId = victim.getParticipantId();
            this.victimDisplayName = victimName;
        }
        this.penalty = penalty;
        this.elapsedSeconds = elapsedSeconds;
        this.periodType = periodType;
        this.periodNumber = periodNumber;
        this.periodElapsedSeconds = periodElapsedSeconds;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public void resolve(MatchTeamAssignment taker, String name, boolean scored,
            UUID goalEventId, UUID shotEventId, Instant now, long resolutionElapsedSeconds,
            MatchPeriodType resolutionPeriodType, Integer resolutionPeriodNumber,
            Long resolutionPeriodElapsedSeconds) {
        if (!penalty || this.scored != null) throw new IllegalStateException();
        this.takerAssignmentId = taker.getId();
        this.takerParticipantType = taker.getParticipantType();
        this.takerParticipantId = taker.getParticipantId();
        this.takerDisplayName = name;
        this.scored = scored;
        this.goalEventId = goalEventId;
        this.shotEventId = shotEventId;
        this.resolvedAt = now;
        this.resolutionElapsedSeconds = resolutionElapsedSeconds;
        this.resolutionPeriodType = resolutionPeriodType;
        this.resolutionPeriodNumber = resolutionPeriodNumber;
        this.resolutionPeriodElapsedSeconds = resolutionPeriodElapsedSeconds;
    }

    public UUID getId() { return id; }
    public UUID getMatchId() { return matchId; }
    public int getOffenderSideNumber() { return offenderSideNumber; }
    public UUID getOffenderAssignmentId() { return offenderAssignmentId; }
    public TeamParticipantType getOffenderParticipantType() { return offenderParticipantType; }
    public UUID getOffenderParticipantId() { return offenderParticipantId; }
    public String getOffenderDisplayName() { return offenderDisplayName; }
    public Integer getVictimSideNumber() { return victimSideNumber; }
    public UUID getVictimAssignmentId() { return victimAssignmentId; }
    public TeamParticipantType getVictimParticipantType() { return victimParticipantType; }
    public UUID getVictimParticipantId() { return victimParticipantId; }
    public String getVictimDisplayName() { return victimDisplayName; }
    public boolean isPenalty() { return penalty; }
    public UUID getTakerAssignmentId() { return takerAssignmentId; }
    public TeamParticipantType getTakerParticipantType() { return takerParticipantType; }
    public UUID getTakerParticipantId() { return takerParticipantId; }
    public String getTakerDisplayName() { return takerDisplayName; }
    public Boolean getScored() { return scored; }
    public UUID getGoalEventId() { return goalEventId; }
    public UUID getShotEventId() { return shotEventId; }
    public long getElapsedSeconds() { return elapsedSeconds; }
    public MatchPeriodType getPeriodType() { return periodType; }
    public Integer getPeriodNumber() { return periodNumber; }
    public Long getPeriodElapsedSeconds() { return periodElapsedSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Long getResolutionElapsedSeconds() { return resolutionElapsedSeconds; }
    public MatchPeriodType getResolutionPeriodType() { return resolutionPeriodType; }
    public Integer getResolutionPeriodNumber() { return resolutionPeriodNumber; }
    public Long getResolutionPeriodElapsedSeconds() { return resolutionPeriodElapsedSeconds; }
}
