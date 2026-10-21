package com.cricpulse.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "players", indexes = @Index(name = "idx_player_team", columnList = "team_id"))
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Default position in the batting line-up (1 = opener). */
    private int battingOrder;

    /** 1-100 skill ratings used by the simulation engine. */
    private int battingSkill;
    private int bowlingSkill;

    private String battingStyle;
    private String bowlingStyle;

    protected Player() {
    }

    public Player(String name, PlayerRole role, Team team, int battingOrder, int battingSkill, int bowlingSkill,
                  String battingStyle, String bowlingStyle) {
        this.name = name;
        this.role = role;
        this.team = team;
        this.battingOrder = battingOrder;
        this.battingSkill = battingSkill;
        this.bowlingSkill = bowlingSkill;
        this.battingStyle = battingStyle;
        this.bowlingStyle = bowlingStyle;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public PlayerRole getRole() { return role; }
    public Team getTeam() { return team; }
    public int getBattingOrder() { return battingOrder; }
    public int getBattingSkill() { return battingSkill; }
    public int getBowlingSkill() { return bowlingSkill; }
    public String getBattingStyle() { return battingStyle; }
    public String getBowlingStyle() { return bowlingStyle; }
}
