package com.onze.api.match;

import java.util.List;
import java.util.UUID;

import com.onze.api.technical.ScoreSource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TeamModels {

    private TeamModels() {
    }

    public record UpdateTeamAssignmentRequest(
            @NotNull @Min(1) Integer teamNumber,
            @NotBlank @Size(max = 48) String assignedRole) {
    }

    public record MatchTeamImageResponse(int teamNumber, String imageUrl) {
    }

    public record TeamAssignmentResponse(
            UUID id,
            TeamParticipantType participantType,
            UUID participantId,
            String displayName,
            String assignedRole,
            Integer overallUsed,
            Integer coverage,
            ScoreSource scoreSource,
            TeamPositionOrigin positionOrigin,
            TeamAssignmentReason reason,
            boolean manuallyChanged) {
    }

    public record TeamResponse(
            int teamNumber,
            Integer estimatedStrength,
            Integer realEvaluations,
            Integer estimatedEvaluations,
            List<TeamAssignmentResponse> assignments) {
    }

    public record MatchTeamsResponse(
            UUID matchId,
            MatchModality modality,
            int teamCount,
            int confirmedPlayers,
            int minimumPlayers,
            int idealPlayers,
            boolean reducedTeams,
            boolean technicalDetailsVisible,
            String generationNotice,
            List<TeamResponse> teams) {
    }
}
