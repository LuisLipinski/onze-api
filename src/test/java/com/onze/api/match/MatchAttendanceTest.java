package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchAttendanceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("20.00");

    @Test
    void shouldCancelPendingChargeWhenPlayerLeaves() {
        MatchAttendance attendance = paidAttendance();

        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);

        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.NOT_GOING);
        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attendance.getPaymentSettlementStatus()).isNull();
    }

    @Test
    void shouldReviewReportedPaymentAndAllowAdminToMarkItAsNotReceived() {
        MatchAttendance attendance = paidAttendance();
        attendance.reportPayment(NOW.minusSeconds(60));

        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.REPORTED);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.REVIEW_REQUIRED);
        assertThat(attendance.getPaymentSettlementRequestedAt()).isEqualTo(NOW);

        attendance.resolveSettlement(
                PaymentSettlementResolution.NOT_RECEIVED,
                PAYMENT_AMOUNT,
                NOW.plusSeconds(60));

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.NOT_RECEIVED);
    }

    @Test
    void shouldOpenSettlementForConfirmedPaymentAndRejectNotReceivedResolution() {
        MatchAttendance attendance = paidAttendance();
        attendance.confirmPayment(NOW.minusSeconds(60));

        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.PENDING);
        assertThatThrownBy(() -> attendance.resolveSettlement(
                PaymentSettlementResolution.NOT_RECEIVED,
                PAYMENT_AMOUNT,
                NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);

        attendance.resolveSettlement(
                PaymentSettlementResolution.REFUNDED,
                PAYMENT_AMOUNT,
                NOW.plusSeconds(60));

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus())
                .isEqualTo(PaymentSettlementStatus.REFUNDED);
        assertThat(attendance.getPaymentSettlementResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldKeepPaidValueBlockedUntilReplacementIsFilled() {
        UUID replacementUserId = UUID.randomUUID();
        MatchAttendance attendance = paidAttendance();
        attendance.confirmPayment(NOW.minusSeconds(60));

        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);
        attendance.requireReplacement(NOW);

        assertThat(attendance.isAwaitingReplacement()).isTrue();
        assertThat(attendance.getReplacementFilledAt()).isNull();

        attendance.fillReplacement(replacementUserId, NOW.plusSeconds(60));

        assertThat(attendance.isAwaitingReplacement()).isFalse();
        assertThat(attendance.getReplacementUserId()).isEqualTo(replacementUserId);
        assertThat(attendance.getReplacementFilledAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldAllowAdministratorToReinstatePaidPlayer() {
        MatchAttendance attendance = paidAttendance();
        attendance.confirmPayment(NOW.minusSeconds(60));
        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);
        attendance.requireReplacement(NOW);

        attendance.reinstateByAdministrator(PAYMENT_AMOUNT, NOW.plusSeconds(60));

        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.GOING);
        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus()).isNull();
        assertThat(attendance.getReplacementRequiredAt()).isNull();
        assertThat(attendance.getAddedAsReplacementAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldCancelOpenSettlementWhenPlayerRejoins() {
        MatchAttendance attendance = paidAttendance();
        attendance.confirmPayment(NOW.minusSeconds(60));
        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);

        attendance.changeStatus(AttendanceStatus.GOING, PAYMENT_AMOUNT, NOW.plusSeconds(60));

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getPaymentSettlementStatus()).isNull();
        assertThat(attendance.getPaymentSettlementRequestedAt()).isNull();
    }

    @Test
    void shouldReserveConsumeAndReturnFullCreditWithoutOpeningCashSettlement() {
        MatchAttendance attendance = new MatchAttendance(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AttendanceStatus.PENDING,
                PAYMENT_AMOUNT);

        attendance.reserveCredit(PAYMENT_AMOUNT, PAYMENT_AMOUNT, NOW.minusSeconds(120));
        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attendance.getCashAmountDue()).isEqualByComparingTo(BigDecimal.ZERO);

        attendance.changeStatus(AttendanceStatus.GOING, PAYMENT_AMOUNT, NOW.minusSeconds(60));
        attendance.consumeCredit(NOW.minusSeconds(60));
        assertThat(attendance.isCreditConsumed()).isTrue();

        attendance.releaseCredit(NOW);
        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);
        attendance.markAutomaticCreditReturn(NOW);

        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attendance.getPaymentSettlementStatus()).isEqualTo(PaymentSettlementStatus.CREDITED);
        assertThat(attendance.getCreditReturnedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldSettleOnlyCashPortionAfterPartialCreditWasReturned() {
        MatchAttendance attendance = new MatchAttendance(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AttendanceStatus.GOING,
                PAYMENT_AMOUNT);
        attendance.reserveCredit(new BigDecimal("15.00"), PAYMENT_AMOUNT, NOW.minusSeconds(120));
        attendance.consumeCredit(NOW.minusSeconds(120));
        attendance.confirmPayment(NOW.minusSeconds(60));

        attendance.releaseCredit(NOW);
        attendance.changeStatus(AttendanceStatus.NOT_GOING, PAYMENT_AMOUNT, NOW);

        assertThat(attendance.getPaymentSettlementStatus()).isEqualTo(PaymentSettlementStatus.PENDING);
        assertThat(attendance.settlementAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void shouldRemoveUnpaidPlayerWhenPaymentDeadlineExpires() {
        MatchAttendance attendance = paidAttendance();

        attendance.removeForMissedPayment(PAYMENT_AMOUNT, NOW);

        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.NOT_GOING);
        assertThat(attendance.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attendance.getPaymentDeadlineRemovedAt()).isEqualTo(NOW);
        assertThat(attendance.getPaymentSettlementStatus()).isNull();
    }

    private MatchAttendance paidAttendance() {
        return new MatchAttendance(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AttendanceStatus.GOING,
                PAYMENT_AMOUNT);
    }
}
