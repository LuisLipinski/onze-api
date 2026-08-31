package com.onze.api.match;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchAttendanceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void shouldCancelPendingChargeWhenPlayerLeaves() {
        MatchAttendance attendance = paidAttendance();

        attendance.changeStatus(AttendanceStatus.NOT_GOING, true, NOW);

        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.NOT_GOING);
        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attendance.getPaymentSettlementStatus()).isNull();
    }

    @Test
    void shouldReviewReportedPaymentAndAllowAdminToMarkItAsNotReceived() {
        MatchAttendance attendance = paidAttendance();
        attendance.reportPayment(NOW.minusSeconds(60));

        attendance.changeStatus(AttendanceStatus.NOT_GOING, true, NOW);

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.REPORTED);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.REVIEW_REQUIRED);
        assertThat(attendance.getPaymentSettlementRequestedAt()).isEqualTo(NOW);

        attendance.resolveSettlement(PaymentSettlementResolution.NOT_RECEIVED, NOW.plusSeconds(60));

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.NOT_RECEIVED);
    }

    @Test
    void shouldOpenSettlementForConfirmedPaymentAndRejectNotReceivedResolution() {
        MatchAttendance attendance = paidAttendance();
        attendance.confirmPayment(NOW.minusSeconds(60));

        attendance.changeStatus(AttendanceStatus.NOT_GOING, true, NOW);

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.PENDING);
        assertThatThrownBy(() -> attendance.resolveSettlement(
                PaymentSettlementResolution.NOT_RECEIVED,
                NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);

        attendance.resolveSettlement(PaymentSettlementResolution.REFUNDED, NOW.plusSeconds(60));

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.REFUNDED);
        assertThat(attendance.getPaymentSettlementResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldCancelOpenSettlementWhenPlayerRejoins() {
        MatchAttendance attendance = paidAttendance();
        attendance.confirmPayment(NOW.minusSeconds(60));
        attendance.changeStatus(AttendanceStatus.NOT_GOING, true, NOW);

        attendance.changeStatus(AttendanceStatus.GOING, true, NOW.plusSeconds(60));

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus()).isNull();
        assertThat(attendance.getPaymentSettlementRequestedAt()).isNull();
    }

    private MatchAttendance paidAttendance() {
        return new MatchAttendance(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AttendanceStatus.GOING,
                true);
    }
}
