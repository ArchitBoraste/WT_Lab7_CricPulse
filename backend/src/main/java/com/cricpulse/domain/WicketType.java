package com.cricpulse.domain;

public enum WicketType {
    BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET;

    /** Whether the bowler is credited with the dismissal. */
    public boolean creditsBowler() {
        return this != RUN_OUT;
    }
}
