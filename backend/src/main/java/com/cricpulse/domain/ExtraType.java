package com.cricpulse.domain;

public enum ExtraType {
    NONE, WIDE, NO_BALL, BYE, LEG_BYE;

    /** A legal delivery counts towards the six balls of an over. */
    public boolean isLegal() {
        return this != WIDE && this != NO_BALL;
    }
}
