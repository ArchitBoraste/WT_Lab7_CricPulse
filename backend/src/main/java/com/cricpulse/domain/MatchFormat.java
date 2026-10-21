package com.cricpulse.domain;

public enum MatchFormat {
    T10(10), T20(20), ODI(50);

    private final int overs;

    MatchFormat(int overs) {
        this.overs = overs;
    }

    public int getOvers() {
        return overs;
    }
}
