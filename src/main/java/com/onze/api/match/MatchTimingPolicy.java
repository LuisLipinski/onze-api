package com.onze.api.match;

final class MatchTimingPolicy {
    static final int MAX_PERIODS = 4;
    static final int MAX_MINUTES = 180;

    private MatchTimingPolicy() { }

    static MatchTimingConfiguration resolve(
            Boolean periodsEnabled,
            Integer periodCount,
            Integer periodDurationMinutes,
            Boolean overtimeEnabled,
            Integer overtimePeriodCount,
            Integer overtimePeriodDurationMinutes,
            Boolean penaltyShootoutEnabled,
            MatchType matchType,
            Integer teamCount) {
        if (!Boolean.TRUE.equals(periodsEnabled)) {
            return MatchTimingConfiguration.disabled();
        }
        requireRange(periodCount, MAX_PERIODS);
        requireRange(periodDurationMinutes, MAX_MINUTES);

        boolean overtime = Boolean.TRUE.equals(overtimeEnabled);
        boolean penalties = Boolean.TRUE.equals(penaltyShootoutEnabled);
        boolean exactlyTwoTeams = matchType == MatchType.VERSUS_EXTERNAL
                || (matchType == MatchType.INTERNAL && Integer.valueOf(2).equals(teamCount));
        if ((overtime || penalties) && !exactlyTwoTeams) {
            throw new InvalidMatchTimingConfigurationException();
        }

        Integer resolvedOvertimePeriods = null;
        Integer resolvedOvertimeMinutes = null;
        if (overtime) {
            requireRange(overtimePeriodCount, MAX_PERIODS);
            requireRange(overtimePeriodDurationMinutes, MAX_MINUTES);
            resolvedOvertimePeriods = overtimePeriodCount;
            resolvedOvertimeMinutes = overtimePeriodDurationMinutes;
        }

        return new MatchTimingConfiguration(
                true,
                periodCount,
                periodDurationMinutes,
                overtime,
                resolvedOvertimePeriods,
                resolvedOvertimeMinutes,
                penalties);
    }

    private static void requireRange(Integer value, int maximum) {
        if (value == null || value < 1 || value > maximum) {
            throw new InvalidMatchTimingConfigurationException();
        }
    }

    record MatchTimingConfiguration(
            boolean periodsEnabled,
            Integer periodCount,
            Integer periodDurationMinutes,
            boolean overtimeEnabled,
            Integer overtimePeriodCount,
            Integer overtimePeriodDurationMinutes,
            boolean penaltyShootoutEnabled) {
        static MatchTimingConfiguration disabled() {
            return new MatchTimingConfiguration(false, null, null, false, null, null, false);
        }
    }

    static final class InvalidMatchTimingConfigurationException extends RuntimeException { }
}
