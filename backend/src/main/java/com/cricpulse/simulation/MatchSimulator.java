package com.cricpulse.simulation;

import com.cricpulse.domain.Innings;
import com.cricpulse.domain.Match;
import com.cricpulse.domain.MatchStatus;
import com.cricpulse.domain.Player;
import com.cricpulse.service.ScoringEngine;
import org.springframework.stereotype.Component;

/** Bowls simulated deliveries through the same {@link ScoringEngine} a human scorer uses. */
@Component
public class MatchSimulator {

    private final ScoringEngine scoring;
    private final BallOutcomeGenerator generator;

    public MatchSimulator(ScoringEngine scoring, BallOutcomeGenerator generator) {
        this.scoring = scoring;
        this.generator = generator;
    }

    public void bowlOne(Match match) {
        Innings inn = match.getCurrentInnings();
        if (match.getStatus() != MatchStatus.LIVE || inn == null) {
            return;
        }
        Player bowler = scoring.ensureBowler(match, inn, null);
        scoring.apply(match, generator.generate(match, inn, inn.getStriker(), bowler));
    }

    /** Bowls up to {@code maxBalls} deliveries, stopping early if the match ends. */
    public int fastForward(Match match, int maxBalls) {
        int bowled = 0;
        while (bowled < maxBalls && match.getStatus() == MatchStatus.LIVE) {
            bowlOne(match);
            bowled++;
        }
        return bowled;
    }
}
