package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
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
            @Min(2) @Max(100) int maxPlayers,
            LocalDate signupDeadlineDate,
            LocalTime signupDeadlineTime,
            LocalDate paymentDeadlineDate,
            LocalTime paymentDeadlineTime,
            Boolean paymentRequired,
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

    public record AttendanceResponse(
            UUID userId,
            String displayName,
            AttendanceStatus status,
            PaymentStatus paymentStatus,
            PaymentSettlementStatus paymentSettlementStatus,
            BigDecimal creditAppliedAmount,
            BigDecimal remainingPaymentAmount,
            CreditAllocationStatus creditAllocationStatus,
            Instant paymentDeadlineRemovedAt,
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
            boolean paymentRequired,
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
            boolean canManage) {
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
