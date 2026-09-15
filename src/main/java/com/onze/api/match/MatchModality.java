package com.onze.api.match;

public enum MatchModality {
    FIELD(11),
    FUT7(7),
    FUTSAL(5);

    private final int playersPerTeam;

    MatchModality(int playersPerTeam) {
        this.playersPerTeam = playersPerTeam;
    }

    public int playersPerTeam() {
        return playersPerTeam;
    }
}
