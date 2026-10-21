package com.cricpulse.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_match_status", columnList = "status"),
        @Index(name = "idx_match_start", columnList = "startTime")
})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team teamA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team teamB;

    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchFormat format;

    private int oversPerInnings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MatchStatus status = MatchStatus.SCHEDULED;

    private Instant startTime;
    private Instant completedAt;
    private Instant lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team tossWinner;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private TossDecision tossDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team winner;

    private String result;

    @ManyToOne(fetch = FetchType.LAZY)
    private Player playerOfMatch;

    /** When true the simulation engine bowls deliveries automatically. */
    private boolean autoSimulate = true;
    private boolean paused;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number ASC")
    private List<Innings> innings = new ArrayList<>();

    protected Match() {
    }

    public Match(Team teamA, Team teamB, String venue, MatchFormat format, Instant startTime, boolean autoSimulate) {
        this.teamA = teamA;
        this.teamB = teamB;
        this.venue = venue;
        this.format = format;
        this.oversPerInnings = format.getOvers();
        this.startTime = startTime;
        this.autoSimulate = autoSimulate;
        this.lastUpdated = Instant.now();
    }

    /** The innings currently in progress, or null when none is. */
    public Innings getCurrentInnings() {
        for (Innings inn : innings) {
            if (!inn.isCompleted()) {
                return inn;
            }
        }
        return null;
    }

    public Team opponentOf(Team team) {
        return team.getId().equals(teamA.getId()) ? teamB : teamA;
    }

    public void touch() {
        this.lastUpdated = Instant.now();
    }

    public Long getId() { return id; }
    public Team getTeamA() { return teamA; }
    public Team getTeamB() { return teamB; }
    public String getVenue() { return venue; }
    public MatchFormat getFormat() { return format; }
    public int getOversPerInnings() { return oversPerInnings; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Team getTossWinner() { return tossWinner; }
    public void setTossWinner(Team tossWinner) { this.tossWinner = tossWinner; }
    public TossDecision getTossDecision() { return tossDecision; }
    public void setTossDecision(TossDecision tossDecision) { this.tossDecision = tossDecision; }
    public Team getWinner() { return winner; }
    public void setWinner(Team winner) { this.winner = winner; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Player getPlayerOfMatch() { return playerOfMatch; }
    public void setPlayerOfMatch(Player playerOfMatch) { this.playerOfMatch = playerOfMatch; }
    public boolean isAutoSimulate() { return autoSimulate; }
    public void setAutoSimulate(boolean autoSimulate) { this.autoSimulate = autoSimulate; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public List<Innings> getInnings() { return innings; }
}
