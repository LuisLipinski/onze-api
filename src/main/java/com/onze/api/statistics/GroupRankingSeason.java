package com.onze.api.statistics;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_ranking_seasons")
public class GroupRankingSeason {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "season_number", nullable = false)
    private int number;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "closed_snapshot", columnDefinition = "text")
    private String closedSnapshot;

    protected GroupRankingSeason() { }

    public GroupRankingSeason(UUID groupId, int number, Instant startedAt) {
        this.groupId = groupId;
        this.number = number;
        this.startedAt = startedAt;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public int getNumber() { return number; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getClosedSnapshot() { return closedSnapshot; }

    public void close(Instant now, String snapshot) {
        if (endedAt != null) throw new IllegalStateException("Season already closed");
        endedAt = now;
        closedSnapshot = snapshot;
    }
}
