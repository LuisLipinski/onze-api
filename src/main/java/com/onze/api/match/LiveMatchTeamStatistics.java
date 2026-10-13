package com.onze.api.match;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "live_match_team_statistics", uniqueConstraints = @UniqueConstraint(
        name = "uk_live_match_team_statistics_match_side", columnNames = {"match_id", "side_number"}))
public class LiveMatchTeamStatistics {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "side_number", nullable = false) private int sideNumber;
    @Column(nullable = false) private int shots;
    @Column(name = "shots_on_target", nullable = false) private int shotsOnTarget;
    @Column(nullable = false) private int corners;
    @Column(nullable = false) private int fouls;

    protected LiveMatchTeamStatistics() { }

    public LiveMatchTeamStatistics(UUID matchId, int sideNumber) {
        this.matchId = matchId;
        this.sideNumber = sideNumber;
    }

    public void adjust(TeamStatisticType type, int delta) {
        if (type == null || (delta != 1 && delta != -1)) throw new IllegalArgumentException();
        int nextShots = shots;
        int nextOnTarget = shotsOnTarget;
        int nextCorners = corners;
        int nextFouls = fouls;
        switch (type) {
            case SHOTS -> nextShots += delta;
            case SHOTS_ON_TARGET -> {
                nextShots += delta;
                nextOnTarget += delta;
            }
            case CORNERS -> nextCorners += delta;
            case FOULS -> nextFouls += delta;
        }
        if (nextShots < 0 || nextOnTarget < 0 || nextShots < nextOnTarget
                || nextCorners < 0 || nextFouls < 0) throw new IllegalArgumentException();
        shots = nextShots;
        shotsOnTarget = nextOnTarget;
        corners = nextCorners;
        fouls = nextFouls;
    }

    public int getSideNumber() { return sideNumber; }
    public int getShots() { return shots; }
    public int getShotsOnTarget() { return shotsOnTarget; }
    public int getCorners() { return corners; }
    public int getFouls() { return fouls; }
}
