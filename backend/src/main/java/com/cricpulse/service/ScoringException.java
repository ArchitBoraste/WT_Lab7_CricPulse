package com.cricpulse.service;

/** A request that breaks the rules of the game or the current match state. */
public class ScoringException extends RuntimeException {

    public ScoringException(String message) {
        super(message);
    }
}
