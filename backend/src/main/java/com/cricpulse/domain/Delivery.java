package com.cricpulse.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** A single ball bowled: the immutable ball-by-ball event log of a match. */
@Entity
@Table(name = "deliveries", indexes = {
        @Index(name = "idx_delivery_innings_seq", columnList = "innings_id, sequence"),
        @Index(name = "idx_delivery_match", columnList = "match_id, id")
})
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "innings_id")
    private Innings innings;

    /** Denormalised so the commentary feed of a match is a single indexed query. */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    private int sequence;
    /** Zero-based over index, e.g. 12 for the 13th over. */
    private int overNumber;
    /** 1-6, the legal ball this delivery belongs to. */
    private int ballInOver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player batter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Player bowler;

    private int runsOffBat;
    /** All extras conceded on this ball, including the one-run wide/no-ball penalty. */
    private int extraRuns;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private ExtraType extraType = ExtraType.NONE;

    private boolean wicket;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private WicketType wicketType;

    @ManyToOne(fetch = FetchType.LAZY)
    private Player dismissedPlayer;

    @Column(length = 600)
    private String commentary;

    private int scoreAfter;
    private int wicketsAfter;
    private Instant createdAt;

    protected Delivery() {
    }

    public Delivery(Innings innings, int sequence, int overNumber, int ballInOver, Player batter, Player bowler,
                    int runsOffBat, int extraRuns, ExtraType extraType) {
        this.innings = innings;
        this.matchId = innings.getMatch().getId();
        this.sequence = sequence;
        this.overNumber = overNumber;
        this.ballInOver = ballInOver;
        this.batter = batter;
        this.bowler = bowler;
        this.runsOffBat = runsOffBat;
        this.extraRuns = extraRuns;
        this.extraType = extraType;
        this.createdAt = Instant.now();
    }

    public int getTotalRuns() {
        return runsOffBat + extraRuns;
    }

    public boolean isLegal() {
        return extraType.isLegal();
    }

    public void markWicket(WicketType type, Player dismissed) {
        this.wicket = true;
        this.wicketType = type;
        this.dismissedPlayer = dismissed;
    }

    public void recordScore(int score, int wickets) {
        this.scoreAfter = score;
        this.wicketsAfter = wickets;
    }

    public Long getId() { return id; }
    public Innings getInnings() { return innings; }
    public Long getMatchId() { return matchId; }
    public int getSequence() { return sequence; }
    public int getOverNumber() { return overNumber; }
    public int getBallInOver() { return ballInOver; }
    public Player getBatter() { return batter; }
    public Player getBowler() { return bowler; }
    public int getRunsOffBat() { return runsOffBat; }
    public int getExtraRuns() { return extraRuns; }
    public ExtraType getExtraType() { return extraType; }
    public boolean isWicket() { return wicket; }
    public WicketType getWicketType() { return wicketType; }
    public Player getDismissedPlayer() { return dismissedPlayer; }
    public String getCommentary() { return commentary; }
    public void setCommentary(String commentary) { this.commentary = commentary; }
    public int getScoreAfter() { return scoreAfter; }
    public int getWicketsAfter() { return wicketsAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
