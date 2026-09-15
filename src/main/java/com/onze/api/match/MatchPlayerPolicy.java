package com.onze.api.match;

final class MatchPlayerPolicy {

    private MatchPlayerPolicy() {
    }

    static PlayerConfiguration resolve(
            MatchModality requestedModality,
            Integer requestedMinimumPlayers,
            int maxPlayers,
            MatchType matchType,
            Integer teamCount) {
        MatchModality modality = requestedModality == null ? MatchModality.FUT7 : requestedModality;
        int sides = matchType == MatchType.INTERNAL ? teamCount : 1;
        int idealPlayers = modality.playersPerTeam() * sides;
        int minimumPlayers = requestedMinimumPlayers == null
                ? Math.min(maxPlayers, idealPlayers)
                : requestedMinimumPlayers;
        if (minimumPlayers <= 0 || minimumPlayers > maxPlayers) {
            throw new InvalidMinimumPlayersException();
        }
        return new PlayerConfiguration(modality, minimumPlayers, idealPlayers);
    }

    record PlayerConfiguration(
            MatchModality modality,
            int minimumPlayers,
            int idealPlayers) {
    }

    static final class InvalidMinimumPlayersException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
