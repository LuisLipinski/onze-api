package com.onze.api.match;

final class MatchFormatPolicy {

    static final int DEFAULT_INTERNAL_TEAM_COUNT = 2;

    private MatchFormatPolicy() {
    }

    static MatchFormat resolve(
            MatchType requestedType,
            Integer requestedTeamCount,
            Integer requestedRequiredGoalkeepers) {
        boolean legacyRequest = requestedType == null;
        MatchType matchType = legacyRequest ? MatchType.INTERNAL : requestedType;
        if (matchType == MatchType.INTERNAL) {
            if (!legacyRequest
                    && (requestedTeamCount == null || requestedRequiredGoalkeepers == null)) {
                throw new InvalidMatchFormatException();
            }
            int teamCount = requestedTeamCount == null
                    ? DEFAULT_INTERNAL_TEAM_COUNT
                    : requestedTeamCount;
            int requiredGoalkeepers = requestedRequiredGoalkeepers == null
                    ? teamCount
                    : requestedRequiredGoalkeepers;
            if (teamCount < 2 || requiredGoalkeepers < teamCount) {
                throw new InvalidMatchFormatException();
            }
            return new MatchFormat(matchType, teamCount, requiredGoalkeepers);
        }

        if (requestedRequiredGoalkeepers == null) {
            throw new InvalidMatchFormatException();
        }
        int requiredGoalkeepers = requestedRequiredGoalkeepers;
        if (requestedTeamCount != null || requiredGoalkeepers < 1) {
            throw new InvalidMatchFormatException();
        }
        return new MatchFormat(matchType, null, requiredGoalkeepers);
    }

    record MatchFormat(
            MatchType matchType,
            Integer teamCount,
            int requiredGoalkeepers) {
    }

    static final class InvalidMatchFormatException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
