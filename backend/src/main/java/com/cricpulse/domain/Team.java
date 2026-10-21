package com.cricpulse.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, length = 5)
    private String shortName;

    @Column(length = 9)
    private String color;

    private String homeGround;

    protected Team() {
    }

    public Team(String name, String shortName, String color, String homeGround) {
        this.name = name;
        this.shortName = shortName;
        this.color = color;
        this.homeGround = homeGround;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
    public String getColor() { return color; }
    public String getHomeGround() { return homeGround; }
}
