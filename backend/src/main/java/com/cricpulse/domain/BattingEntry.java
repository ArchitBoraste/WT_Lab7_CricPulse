package com.cricpulse.domain;

import jakarta.persistence.*;

/** One batter's line on the scorecard of an innings. */
@Entity
@Table(name = "batting_entries", indexes = {
        @Index(name = "idx_bat_innings", columnList = "innings_id"),
        @Index(name = "idx_bat_player", columnList = "player_id")
})
public class BattingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "innings_id")
    private Innings innings;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    private int position;
    private int runs;
    private int balls;
    private int fours;
    private int sixes;
    private boolean out;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private WicketType wicketType;

    private String dismissal;

    protected BattingEntry() {
    }

    public BattingEntry(Innings innings, Player player, int position) {
        this.innings = innings;
        this.player = player;
        this.position = position;
    }

    public void addRuns(int r) {
        runs += r;
        if (r == 4) fours++;
        if (r == 6) sixes++;
    }

    public void addBall() { balls++; }

    public void dismiss(WicketType type, String description) {
        this.out = true;
        this.wicketType = type;
        this.dismissal = description;
    }

    public Long getId() { return id; }
    public Innings getInnings() { return innings; }
    public Player getPlayer() { return player; }
    public int getPosition() { return position; }
    public int getRuns() { return runs; }
    public int getBalls() { return balls; }
    public int getFours() { return fours; }
    public int getSixes() { return sixes; }
    public boolean isOut() { return out; }
    public WicketType getWicketType() { return wicketType; }
    public String getDismissal() { return dismissal; }
}
