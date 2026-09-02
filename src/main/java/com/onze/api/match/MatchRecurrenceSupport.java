package com.onze.api.match;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class MatchRecurrenceSupport {

    static final LocalTime WEEKLY_ATTENDANCE_OPENING_TIME = LocalTime.of(9, 0);

    private MatchRecurrenceSupport() {
    }

    static FootballMatch nextOccurrence(FootballMatch previous, MatchSeries series) {
        ZoneId zoneId = ZoneId.of(series.getTimeZone());
        ZonedDateTime previousStart = previous.getStartsAt().atZone(zoneId);
        Instant nextStart = previousStart.plusWeeks(1).toInstant();
        Instant nextAttendanceOpening = previousStart.toLocalDate()
                .plusDays(1)
                .atTime(WEEKLY_ATTENDANCE_OPENING_TIME)
                .atZone(zoneId)
                .toInstant();
        Instant nextSignupDeadline = previous.getSignupDeadline()
                .atZone(zoneId)
                .plusWeeks(1)
                .toInstant();
        Instant nextPaymentDeadline = previous.getPaymentDeadline() == null
                ? null
                : previous.getPaymentDeadline()
                        .atZone(zoneId)
                        .plusWeeks(1)
                        .toInstant();

        return new FootballMatch(
                series.getGroupId(),
                series.getId(),
                previous.getOccurrenceNumber() + 1,
                nextStart,
                series.getTimeZone(),
                series.getVenue(),
                series.getMaxPlayers(),
                series.getPaymentAmount(),
                series.getPixKey(),
                series.getNotes(),
                nextAttendanceOpening,
                null,
                nextSignupDeadline,
                nextPaymentDeadline,
                series.getCreatedBy());
    }
}
