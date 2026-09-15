package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.onze.api.group.PlayerPosition;
import com.onze.api.technical.PlayerSkill;
import com.onze.api.technical.TechnicalProfileModels.OverallResponse;
import com.onze.api.technical.TechnicalProfileModels.PositionOverallResponse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MatchModels {

    private MatchModels() {
    }

    public record CreateMatchRequest(
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotBlank @Size(max = 64) String timeZone,
            @NotBlank @Size(max = 255) String venue,
            @Min(2) int maxPlayers,
            MatchType matchType,
            Integer teamCount,
            Integer requiredGoalkeepers,
            MatchModality modality,
            Integer minimumPlayers,
            LocalDate signupDeadlineDate,
            LocalTime signupDeadlineTime,
            LocalDate paymentDeadlineDate,
            LocalTime paymentDeadlineTime,
            Boolean paymentRequired,
            Boolean goalkeeperPays,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal paymentAmount,
            @Size(max = 255) String pixKey,
            @Size(max = 1000) String notes,
            @NotNull MatchRecurrence recurrence) {
    }

    public record UpdateAttendanceRequest(
            @NotNull AttendanceStatus status) {
    }

    public record ResolvePaymentSettlementRequest(
            @NotNull PaymentSettlementResolution resolution) {
    }

    public record BulkResolvePaymentSettlementsRequest(
            @NotEmpty @Size(max = 100) List<@NotNull UUID> playerUserIds,
            @NotNull PaymentSettlementResolution resolution) {
    }

    public record AddMatchReplacementRequest(
            @NotNull UUID replacementUserId) {
    }

    public record UpdateMatchGoalkeeperRequest(
            @NotNull Boolean isGoalkeeper) {
    }

    public record AddRentalGoalkeeperRequest(
            @NotBlank @Size(max = 120) String displayName) {
    }

    public record RentalGoalkeeperResponse(
            UUID id,
            String displayName,
            Instant createdAt) {
    }

    public record AttendanceResponse(
            UUID userId,
            String displayName,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            boolean canPlayGoalkeeper,
            AttendanceStatus status,
            boolean isGoalkeeper,
            boolean paymentExempt,
            PaymentStatus paymentStatus,
            PaymentSettlementStatus paymentSettlementStatus,
            BigDecimal creditAppliedAmount,
            BigDecimal remainingPaymentAmount,
            CreditAllocationStatus creditAllocationStatus,
            Instant paymentDeadlineRemovedAt,
            Instant replacementRequiredAt,
            UUID replacementUserId,
            String replacementDisplayName,
            Instant replacementFilledAt,
            Instant addedAsReplacementAt,
            UUID replacementForUserId,
            boolean settlementAvailable,
            boolean currentUser) {
    }

    public record PlayerCreditResponse(
            UUID userId,
            String displayName,
            BigDecimal availableAmount,
            BigDecimal allocatedAmount,
            CreditAllocationStatus allocationStatus,
            UUID allocatedMatchId,
            Instant allocatedMatchStartsAt,
            boolean currentUser) {
    }

    public record MatchResponse(
            UUID id,
            UUID groupId,
            String groupName,
            UUID seriesId,
            MatchRecurrence recurrence,
            boolean seriesActive,
            Instant startsAt,
            String timeZone,
            String venue,
            int maxPlayers,
            MatchType matchType,
            Integer teamCount,
            int requiredGoalkeepers,
            MatchModality modality,
            int minimumPlayers,
            int idealPlayers,
            int missingMinimumPlayers,
            int currentGoalkeepers,
            int missingGoalkeepers,
            boolean goalkeeperDecisionRequired,
            boolean secondaryGoalkeeperDecisionRequired,
            boolean paymentRequired,
            boolean goalkeeperPays,
            BigDecimal paymentAmount,
            String pixKey,
            String notes,
            MatchStatus status,
            Instant attendanceOpensAt,
            boolean attendanceOpen,
            Instant signupDeadline,
            boolean signupOpen,
            Instant paymentDeadline,
            boolean paymentOpen,
            boolean canReportPayment,
            boolean canJoin,
            boolean canWithdraw,
            AttendanceStatus myAttendance,
            PaymentStatus myPaymentStatus,
            PaymentSettlementStatus myPaymentSettlementStatus,
            BigDecimal myCreditAppliedAmount,
            BigDecimal myRemainingPaymentAmount,
            CreditAllocationStatus myCreditAllocationStatus,
            Instant myPaymentDeadlineRemovedAt,
            int goingCount,
            int notGoingCount,
            List<AttendanceResponse> attendances,
            List<RentalGoalkeeperResponse> rentalGoalkeepers,
            List<GuestResponse> guests,
            boolean teamsGenerated,
            boolean canViewTechnical,
            boolean canManage) {
    }

    public record UpdateMatchPlayerConfigurationRequest(
            @NotNull MatchModality modality,
            @NotNull @Min(1) Integer minimumPlayers,
            @NotNull @Min(2) Integer maxPlayers) {
    }

    public record AddGuestRequest(
            @NotBlank @Size(max = 120) String displayName,
            @NotNull PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            Map<PlayerSkill, Integer> ratings) {
    }

    public record GuestResponse(
            UUID id,
            String displayName,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            Boolean evaluated,
            Instant createdAt) {
    }

    public record UpdateGuestTechnicalProfileRequest(
            @NotNull @Size(max = 17) Map<@NotNull PlayerSkill, Integer> ratings) {
    }

    public record GuestTechnicalProfileResponse(
            UUID guestId,
            String displayName,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            Map<PlayerSkill, Integer> ratings,
            OverallResponse generalOverall,
            List<PositionOverallResponse> positionOveralls,
            Map<PlayerPosition, List<PlayerSkill>> importantSkills,
            Instant technicalProfileUpdatedAt) {
    }

    public record PushTokenRequest(
            @NotBlank
            @Size(max = 255)
            @Pattern(regexp = "^(Expo(nent)?PushToken)\\[[A-Za-z0-9_-]+]$")
            String token) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
