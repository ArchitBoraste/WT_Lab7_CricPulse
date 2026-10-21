package com.cricpulse.domain;

import jakarta.persistence.*;

/** One bowler's figures in an innings. */
@Entity
@Table(name = "bowling_entries", indexes = {
        @Index(name = "idx_bowl_innings", columnList = "innings_id"),
        @Index(name = "idx_bowl_player", columnList = "player_id")
})
public class BowlingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "innings_id")
    private Innings innings;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    private int legalBalls;
    private int runs;
    private int wickets;
    private int maidens;
    private int wides;
    private int noBalls;
    private int dots;

    protected BowlingEntry() {
    }

    public BowlingEntry(Innings innings, Player player) {
        this.innings = innings;
        this.player = player;
    }

    public void addRuns(int r) { runs += r; }
    public void addLegalBall() { legalBalls++; }
    public void addWicket() { wickets++; }
    public void addMaiden() { maidens++; }
    public void addWide() { wides++; }
    public void addNoBall() { noBalls++; }
    public void addDot() { dots++; }

    public Long getId() { return id; }
    public Innings getInnings() { return innings; }
    public Player getPlayer() { return player; }
    public int getLegalBalls() { return legalBalls; }
    public int getRuns() { return runs; }
    public int getWickets() { return wickets; }
    public int getMaidens() { return maidens; }
    public int getWides() { return wides; }
    public int getNoBalls() { return noBalls; }
    public int getDots() { return dots; }
}
