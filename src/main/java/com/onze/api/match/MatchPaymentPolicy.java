package com.onze.api.match;

import java.math.BigDecimal;
import java.time.Instant;

final class MatchPaymentPolicy {

    private MatchPaymentPolicy() {
    }

    static boolean isExempt(FootballMatch match, MatchAttendance attendance) {
        return match.isPaymentRequired()
                && !match.isGoalkeeperPays()
                && attendance != null
                && attendance.isGoalkeeper();
    }

    static boolean requiresPayment(FootballMatch match, MatchAttendance attendance) {
        return match.isPaymentRequired() && !isExempt(match, attendance);
    }

    static BigDecimal effectiveAmount(FootballMatch match, MatchAttendance attendance) {
        return requiresPayment(match, attendance) ? match.getPaymentAmount() : null;
    }

    static boolean paymentWindowOpen(FootballMatch match, MatchAttendance attendance, Instant now) {
        return match.isPaymentOpen(now)
                || (match.getPaymentDeadline() != null
                        && attendance.getAddedAsReplacementAt() != null
                        && !attendance.getAddedAsReplacementAt().isBefore(match.getPaymentDeadline()));
    }
}
