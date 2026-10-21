package com.cricpulse.service;

import com.cricpulse.domain.BattingEntry;
import com.cricpulse.domain.Delivery;
import com.cricpulse.domain.ExtraType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Turns a delivery into a line of ball-by-ball commentary. */
@Component
public class CommentaryGenerator {

    private static final List<String> DOT = List.of(
            "good length, defended solidly back to the bowler.",
            "beaten outside off! That one shaped away late.",
            "straight to the fielder at cover, no run.",
            "short of a length, ducks under it.",
            "tight line on off stump, left alone.",
            "full and straight, dug out towards mid-on.",
            "slower ball, mistimed to point.");
    private static final List<String> SINGLE = List.of(
            "pushed to long-on for a single.",
            "worked off the pads to square leg, they scamper through.",
            "dabbed to third man for one.",
            "nudged into the gap at midwicket and they take one.",
            "tapped to cover and a quick single.");
    private static final List<String> TWO = List.of(
            "driven into the gap at deep cover, they come back for two.",
            "flicked behind square, good running gets them two.",
            "pulled to deep midwicket, two runs.");
    private static final List<String> THREE = List.of(
            "timed beautifully through the covers, the fielder cuts it off just inside the rope. Three.",
            "chipped over mid-on and they run hard for three.");
    private static final List<String> FOUR = List.of(
            "FOUR! Cracking drive through the covers.",
            "FOUR! Pulled away fiercely to the midwicket fence.",
            "FOUR! Edged, and it races away past the keeper to third man.",
            "FOUR! Short and wide, carved over point.",
            "FOUR! Glorious straight drive, past the bowler in a flash.",
            "FOUR! Swept fine, no chance for the fielder.");
    private static final List<String> SIX = List.of(
            "SIX! Launched over long-on, that is huge!",
            "SIX! Picked the length early and dispatched it over midwicket.",
            "SIX! Stepped out and lofted it straight back over the bowler's head.",
            "SIX! Ramp shot over the keeper, outrageous!",
            "SIX! Into the second tier, what a hit!");
    private static final List<String> WIDE = List.of(
            "wide, drifting down the leg side.",
            "wide, way outside off stump.",
            "wide, bouncer sails over the batter's head.");

    public String describe(Delivery d, BattingEntry batterAfter, String dismissal) {
        String batter = d.getBatter().getName();
        String prefix = d.getBowler().getName() + " to " + batter + ", ";
        StringBuilder text = new StringBuilder(prefix);

        if (d.isWicket()) {
            text.append(switch (d.getWicketType()) {
                case BOWLED -> "OUT! Bowled him! Crashes into the stumps.";
                case CAUGHT -> "OUT! Caught! Goes for the big shot and finds the fielder.";
                case LBW -> "OUT! LBW! Trapped right in front.";
                case RUN_OUT -> "OUT! Run out! A mix-up in the middle and a direct hit.";
                case STUMPED -> "OUT! Stumped! Down the track, misses, and the keeper does the rest.";
                case HIT_WICKET -> "OUT! Hit wicket! Treads on the stumps going back.";
            });
            String outName = d.getDismissedPlayer() != null ? d.getDismissedPlayer().getName() : batter;
            text.append(' ').append(outName).append(' ').append(dismissal);
            if (batterAfter != null) {
                text.append(' ').append(batterAfter.getRuns()).append(" (").append(batterAfter.getBalls()).append(')');
            }
            if (d.getTotalRuns() > 0) {
                text.append(" (").append(d.getTotalRuns()).append(" run").append(d.getTotalRuns() == 1 ? "" : "s").append(" completed)");
            }
            return text.toString();
        }

        ExtraType extra = d.getExtraType();
        switch (extra) {
            case WIDE -> text.append(pick(WIDE)).append(d.getExtraRuns() > 1 ? " " + (d.getExtraRuns() - 1) + " more taken." : "");
            case NO_BALL -> {
                text.append("NO BALL! Overstepped.");
                if (d.getRunsOffBat() > 0) text.append(' ').append(d.getRunsOffBat()).append(" off the bat as well.");
            }
            case BYE -> text.append(d.getExtraRuns()).append(d.getExtraRuns() == 4 ? " byes, beats the keeper and runs away to the fence." : " bye, the keeper fumbles.");
            case LEG_BYE -> text.append(d.getExtraRuns()).append(" leg bye").append(d.getExtraRuns() == 1 ? "" : "s").append(", off the pad.");
            case NONE -> text.append(switch (d.getRunsOffBat()) {
                case 0 -> pick(DOT);
                case 1 -> pick(SINGLE);
                case 2 -> pick(TWO);
                case 3 -> pick(THREE);
                case 4 -> pick(FOUR);
                case 6 -> pick(SIX);
                default -> d.getRunsOffBat() + " runs, overthrows add to the tally.";
            });
        }

        if (batterAfter != null) {
            int after = batterAfter.getRuns();
            int before = after - d.getRunsOffBat();
            if (before < 100 && after >= 100) {
                text.append(" HUNDRED for ").append(batter).append("! ").append(after).append(" off ").append(batterAfter.getBalls()).append('.');
            } else if (before < 50 && after >= 50) {
                text.append(" FIFTY for ").append(batter).append("! ").append(after).append(" off ").append(batterAfter.getBalls()).append('.');
            }
        }
        return text.toString();
    }

    private static String pick(List<String> options) {
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }
}
