package com.cricpulse.simulation;

import com.cricpulse.domain.*;
import com.cricpulse.dto.Requests.DeliveryRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Probabilistic model of a single delivery. Base T20 rates are skewed by the batter-vs-bowler
 * skill gap, the phase of the innings (powerplay, middle, death) and the pressure of a chase.
 */
@Component
public class BallOutcomeGenerator {

    private enum Outcome { DOT, ONE, TWO, THREE, FOUR, SIX, WICKET, WIDE, NO_BALL, LEG_BYE, BYE }

    public DeliveryRequest generate(Match match, Innings inn, Player striker, Player bowler) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double skill = (striker.getBattingSkill() - bowler.getBowlingSkill()) / 100.0; // -1 .. 1

        int totalBalls = match.getOversPerInnings() * 6;
        double progress = (double) inn.getLegalBalls() / totalBalls;
        boolean powerplay = progress < 0.3;
        boolean death = progress >= 0.8;

        double aggression = death ? 0.6 : powerplay ? 0.3 : 0.1;
        if (inn.getTarget() != null) {
            int ballsLeft = Math.max(1, totalBalls - inn.getLegalBalls());
            double rrr = (inn.getTarget() - inn.getRuns()) * 6.0 / ballsLeft;
            double baseline = match.getFormat() == MatchFormat.ODI ? 6 : match.getFormat() == MatchFormat.T10 ? 11 : 9;
            aggression += Math.max(-0.2, Math.min(0.9, (rrr - baseline) / 6));
        }
        if (match.getFormat() == MatchFormat.ODI) {
            aggression -= 0.35;
        }

        double[] w = new double[Outcome.values().length];
        w[Outcome.DOT.ordinal()] = 0.34 * (1 - 0.3 * skill) * (1 - 0.15 * aggression);
        w[Outcome.ONE.ordinal()] = 0.36;
        w[Outcome.TWO.ordinal()] = 0.07;
        w[Outcome.THREE.ordinal()] = 0.008;
        w[Outcome.FOUR.ordinal()] = 0.105 * (1 + 0.6 * skill + 0.35 * aggression) * (powerplay ? 1.25 : 1);
        w[Outcome.SIX.ordinal()] = 0.045 * (1 + 0.9 * skill + 0.9 * aggression) * (death ? 1.4 : 1);
        w[Outcome.WICKET.ordinal()] = 0.045 * (1 - 0.6 * skill) * (1 + 0.5 * aggression)
                * (striker.getBattingSkill() < 35 ? 1.8 : 1);
        w[Outcome.WIDE.ordinal()] = 0.03;
        w[Outcome.NO_BALL.ordinal()] = 0.006;
        w[Outcome.LEG_BYE.ordinal()] = 0.014;
        w[Outcome.BYE.ordinal()] = 0.004;

        Outcome o = pick(w, rnd);
        return switch (o) {
            case DOT -> runs(0);
            case ONE -> runs(1);
            case TWO -> runs(2);
            case THREE -> runs(3);
            case FOUR -> runs(4);
            case SIX -> runs(6);
            case WIDE -> extra(ExtraType.WIDE, rnd.nextDouble() < 0.05 ? 4 : 0, 0);
            case NO_BALL -> extra(ExtraType.NO_BALL, 0, new int[]{0, 0, 1, 1, 4, 6}[rnd.nextInt(6)]);
            case LEG_BYE -> extra(ExtraType.LEG_BYE, rnd.nextDouble() < 0.2 ? 4 : 1, 0);
            case BYE -> extra(ExtraType.BYE, rnd.nextDouble() < 0.3 ? 4 : 1, 0);
            case WICKET -> wicket(inn, bowler, rnd);
        };
    }

    private DeliveryRequest wicket(Innings inn, Player bowler, ThreadLocalRandom rnd) {
        boolean spinner = bowler.getBowlingStyle() != null && bowler.getBowlingStyle().toLowerCase().contains("spin");
        double r = rnd.nextDouble();
        WicketType type;
        if (r < 0.20) type = WicketType.BOWLED;
        else if (r < 0.74) type = WicketType.CAUGHT;
        else if (r < 0.88) type = WicketType.LBW;
        else if (r < 0.95) type = WicketType.RUN_OUT;
        else if (r < 0.99) type = spinner ? WicketType.STUMPED : WicketType.CAUGHT;
        else type = WicketType.HIT_WICKET;

        Long fielder = type == WicketType.CAUGHT && rnd.nextDouble() < 0.08 ? bowler.getId() : null;
        if (type == WicketType.RUN_OUT) {
            boolean nonStriker = rnd.nextDouble() < 0.35 && inn.getNonStriker() != null;
            Long dismissed = nonStriker ? inn.getNonStriker().getId() : null;
            int completed = rnd.nextDouble() < 0.4 ? 1 : 0;
            return new DeliveryRequest(completed, ExtraType.NONE, 0, true, type, dismissed, null, null, null);
        }
        return new DeliveryRequest(0, ExtraType.NONE, 0, true, type, null, fielder, null, null);
    }

    private static DeliveryRequest runs(int r) {
        return new DeliveryRequest(r, ExtraType.NONE, 0, false, null, null, null, null, null);
    }

    private static DeliveryRequest extra(ExtraType type, int extraRuns, int batRuns) {
        return new DeliveryRequest(batRuns, type, extraRuns, false, null, null, null, null, null);
    }

    private static Outcome pick(double[] weights, ThreadLocalRandom rnd) {
        double total = 0;
        for (double v : weights) total += Math.max(0, v);
        double r = rnd.nextDouble(total);
        for (int i = 0; i < weights.length; i++) {
            r -= Math.max(0, weights[i]);
            if (r <= 0) return Outcome.values()[i];
        }
        return Outcome.DOT;
    }
}
