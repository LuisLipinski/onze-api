package com.onze.api.match;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public final class MatchMinimumPlayerDecisionModels {

    private MatchMinimumPlayerDecisionModels() {
    }

    public record ExtendSignupDeadlineRequest(
            @NotNull LocalDate signupDeadlineDate,
            @NotNull LocalTime signupDeadlineTime,
            LocalDate paymentDeadlineDate,
            LocalTime paymentDeadlineTime) {
    }

    public record MinimumPlayerDecisionResponse(
            UUID matchId,
            int confirmedPlayers,
            int minimumPlayers,
            Instant signupDeadline,
            Instant paymentDeadline,
            boolean decisionRequired,
            boolean belowMinimumApproved) {
    }
}
