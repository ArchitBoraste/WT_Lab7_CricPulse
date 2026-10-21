package com.cricpulse.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "innings", indexes = @Index(name = "idx_innings_match", columnList = "match_id"))
public class Innings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    private int number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team battingTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Team bowlingTeam;

    private int runs;
    private int wickets;
    private int legalBalls;
    private int wides;
    private int noBalls;
    private int byes;
    private int legByes;

    /** Runs required to win (second innings only). */
    private Integer target;

    private boolean completed;

    @ManyToOne(fetch = FetchType.LAZY)
    private Player striker;

    @ManyToOne(fetch = FetchType.LAZY)
    private Player nonStriker;

    /** Null between overs, until the next bowler is chosen. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Player currentBowler;

    @ManyToOne(fetch = FetchType.LAZY)
    private Player lastBowler;

    /** Runs conceded by the bowler in the over in progress (for maidens). */
    private int currentOverRuns;

    private int partnershipRuns;
    private int partnershipBalls;

    /** Running count of deliveries (legal and illegal) bowled in this innings. */
    private int deliveryCount;

    /** Wickets that end the innings: one less than the number of players available to bat. */
    private int maxWickets = 10;

    @OneToMany(mappedBy = "innings", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BattingEntry> batting = new ArrayList<>();

    @OneToMany(mappedBy = "innings", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BowlingEntry> bowling = new ArrayList<>();

    protected Innings() {
    }

    public Innings(Match match, int number, Team battingTeam, Team bowlingTeam, Integer target) {
        this.match = match;
        this.number = number;
        this.battingTeam = battingTeam;
        this.bowlingTeam = bowlingTeam;
        this.target = target;
    }

    public int getExtras() {
        return wides + noBalls + byes + legByes;
    }

    public void swapStrike() {
        Player tmp = striker;
        striker = nonStriker;
        nonStriker = tmp;
    }

    public BattingEntry battingEntryFor(Player player) {
        if (player == null) return null;
        return batting.stream().filter(b -> b.getPlayer().getId().equals(player.getId())).findFirst().orElse(null);
    }

    public BowlingEntry bowlingEntryFor(Player player) {
        if (player == null) return null;
        return bowling.stream().filter(b -> b.getPlayer().getId().equals(player.getId())).findFirst().orElse(null);
    }

    public void addRuns(int r) { runs += r; partnershipRuns += r; }
    public void addLegalBall() { legalBalls++; partnershipBalls++; }
    public void addWicket() { wickets++; partnershipRuns = 0; partnershipBalls = 0; }
    public void addWides(int r) { wides += r; }
    public void addNoBalls(int r) { noBalls += r; }
    public void addByes(int r) { byes += r; }
    public void addLegByes(int r) { legByes += r; }
    public void addBowlerOverRuns(int r) { currentOverRuns += r; }
    public int nextDeliverySequence() { return ++deliveryCount; }

    public Long getId() { return id; }
    public Match getMatch() { return match; }
    public int getNumber() { return number; }
    public Team getBattingTeam() { return battingTeam; }
    public Team getBowlingTeam() { return bowlingTeam; }
    public int getRuns() { return runs; }
    public int getWickets() { return wickets; }
    public int getLegalBalls() { return legalBalls; }
    public int getWides() { return wides; }
    public int getNoBalls() { return noBalls; }
    public int getByes() { return byes; }
    public int getLegByes() { return legByes; }
    public Integer getTarget() { return target; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Player getStriker() { return striker; }
    public void setStriker(Player striker) { this.striker = striker; }
    public Player getNonStriker() { return nonStriker; }
    public void setNonStriker(Player nonStriker) { this.nonStriker = nonStriker; }
    public Player getCurrentBowler() { return currentBowler; }
    public void setCurrentBowler(Player currentBowler) { this.currentBowler = currentBowler; }
    public Player getLastBowler() { return lastBowler; }
    public void setLastBowler(Player lastBowler) { this.lastBowler = lastBowler; }
    public int getCurrentOverRuns() { return currentOverRuns; }
    public void setCurrentOverRuns(int currentOverRuns) { this.currentOverRuns = currentOverRuns; }
    public int getPartnershipRuns() { return partnershipRuns; }
    public int getPartnershipBalls() { return partnershipBalls; }
    public int getDeliveryCount() { return deliveryCount; }
    public int getMaxWickets() { return maxWickets; }
    public void setMaxWickets(int maxWickets) { this.maxWickets = maxWickets; }

    public boolean isAllOut() {
        return wickets >= maxWickets;
    }
    public List<BattingEntry> getBatting() { return batting; }
    public List<BowlingEntry> getBowling() { return bowling; }
}
