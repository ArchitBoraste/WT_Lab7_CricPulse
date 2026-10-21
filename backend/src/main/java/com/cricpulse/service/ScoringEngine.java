package com.cricpulse.service;

import com.cricpulse.domain.*;
import com.cricpulse.dto.Requests.DeliveryRequest;
import com.cricpulse.repository.DeliveryRepository;
import com.cricpulse.repository.PlayerRepository;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the Laws of Cricket (the parts that matter for scoring) to one delivery:
 * runs and extras, strike rotation, overs, wickets, and innings/match completion.
 * Both the live simulation and human scorers go through this class, so they share one rule set.
 * Must be called inside a transaction while holding the match lock.
 */
@Component
public class ScoringEngine {

    private static final Set<WicketType> ON_WIDE = EnumSet.of(WicketType.RUN_OUT, WicketType.STUMPED);
    private static final Set<WicketType> ON_OTHER_EXTRAS = EnumSet.of(WicketType.RUN_OUT);

    private final PlayerRepository players;
    private final DeliveryRepository deliveries;
    private final SelectionPolicy selection;
    private final MatchLifecycle lifecycle;
    private final CommentaryGenerator commentary;

    public ScoringEngine(PlayerRepository players, DeliveryRepository deliveries, SelectionPolicy selection,
                         MatchLifecycle lifecycle, CommentaryGenerator commentary) {
        this.players = players;
        this.deliveries = deliveries;
        this.selection = selection;
        this.lifecycle = lifecycle;
        this.commentary = commentary;
    }

    /** Returns the bowler for the next ball, choosing one when a new over is starting. */
    public Player ensureBowler(Match match, Innings inn, Long requestedBowlerId) {
        Player current = inn.getCurrentBowler();
        if (current != null) {
            if (requestedBowlerId != null && !requestedBowlerId.equals(current.getId())) {
                throw new ScoringException("Cannot change the bowler in the middle of an over");
            }
            return current;
        }
        Player bowler;
        if (requestedBowlerId != null) {
            bowler = players.findById(requestedBowlerId).orElseThrow(() -> new NotFoundException("Player", requestedBowlerId));
            if (!bowler.getTeam().getId().equals(inn.getBowlingTeam().getId())) {
                throw new ScoringException(bowler.getName() + " is not in the bowling side");
            }
            if (same(inn.getLastBowler(), bowler)) {
                throw new ScoringException(bowler.getName() + " bowled the previous over and cannot bowl consecutive overs");
            }
            BowlingEntry figures = inn.bowlingEntryFor(bowler);
            if (figures != null && figures.getLegalBalls() >= selection.maxBallsPerBowler(match)) {
                throw new ScoringException(bowler.getName() + " has completed their quota of overs");
            }
        } else {
            bowler = selection.chooseBowler(match, inn);
        }
        inn.setCurrentBowler(bowler);
        inn.setCurrentOverRuns(0);
        if (inn.bowlingEntryFor(bowler) == null) {
            inn.getBowling().add(new BowlingEntry(inn, bowler));
        }
        return bowler;
    }

    public Delivery apply(Match match, DeliveryRequest req) {
        if (match.getStatus() != MatchStatus.LIVE) {
            throw new ScoringException("Match " + match.getId() + " is not live");
        }
        Innings inn = match.getCurrentInnings();
        if (inn == null) {
            throw new ScoringException("No innings in progress");
        }
        ExtraType extra = req.extraTypeOrNone();
        validate(req, extra);
        validateDismissal(req, inn);

        Player bowler = ensureBowler(match, inn, req.bowlerId());
        Player striker = inn.getStriker();
        Player nonStriker = inn.getNonStriker();
        BattingEntry bat = inn.battingEntryFor(striker);
        BowlingEntry bowl = inn.bowlingEntryFor(bowler);

        int overNumber = inn.getLegalBalls() / 6;
        int ballInOver = inn.getLegalBalls() % 6 + 1;
        int batRuns = req.runsOffBat();

        int extraRuns;   // all extras on this ball, including the one-run penalty
        int bowlerRuns;  // runs charged to the bowler's analysis
        int runsRun;     // runs physically run, which decides strike rotation
        switch (extra) {
            case WIDE -> {
                extraRuns = 1 + req.extraRuns();
                bowlerRuns = extraRuns;
                runsRun = req.extraRuns();
                inn.addWides(extraRuns);
                bowl.addWide();
            }
            case NO_BALL -> {
                extraRuns = 1 + req.extraRuns();
                bowlerRuns = 1 + batRuns;
                runsRun = batRuns + req.extraRuns();
                inn.addNoBalls(1);
                inn.addByes(req.extraRuns());
                bowl.addNoBall();
            }
            case BYE -> {
                extraRuns = req.extraRuns();
                bowlerRuns = 0;
                runsRun = extraRuns;
                inn.addByes(extraRuns);
            }
            case LEG_BYE -> {
                extraRuns = req.extraRuns();
                bowlerRuns = 0;
                runsRun = extraRuns;
                inn.addLegByes(extraRuns);
            }
            default -> {
                extraRuns = 0;
                bowlerRuns = batRuns;
                runsRun = batRuns;
            }
        }

        // A wide is not a ball faced; a no-ball is.
        if (extra != ExtraType.WIDE) {
            bat.addBall();
        }
        bat.addRuns(batRuns);
        bowl.addRuns(bowlerRuns);
        if (extra.isLegal()) {
            bowl.addLegalBall();
            inn.addLegalBall();
            if (bowlerRuns == 0) bowl.addDot();
        }
        inn.addRuns(batRuns + extraRuns);
        inn.addBowlerOverRuns(bowlerRuns);

        Delivery delivery = new Delivery(inn, inn.nextDeliverySequence(), overNumber, ballInOver,
                striker, bowler, batRuns, extraRuns, extra);

        Player dismissed = null;
        String dismissal = null;
        if (req.wicket()) {
            dismissed = req.dismissedPlayerId() == null || req.dismissedPlayerId().equals(striker.getId())
                    ? striker : nonStriker;
            Player fielder = resolveFielder(req, inn, bowler);
            dismissal = describeDismissal(req.wicketType(), bowler, fielder);
            inn.battingEntryFor(dismissed).dismiss(req.wicketType(), dismissal);
            if (req.wicketType().creditsBowler()) {
                bowl.addWicket();
            }
            inn.addWicket();
            delivery.markWicket(req.wicketType(), dismissed);
        }

        if (runsRun % 2 == 1) {
            inn.swapStrike();
        }

        if (dismissed != null) {
            Player next = inn.isAllOut() ? null : selection.nextBatter(inn);
            if (next != null) {
                inn.getBatting().add(new BattingEntry(inn, next, inn.getBatting().size() + 1));
            }
            if (same(inn.getStriker(), dismissed)) {
                inn.setStriker(next);
            } else {
                inn.setNonStriker(next);
            }
        }

        boolean overComplete = extra.isLegal() && inn.getLegalBalls() % 6 == 0;
        if (overComplete) {
            if (inn.getCurrentOverRuns() == 0) {
                bowl.addMaiden();
            }
            inn.swapStrike();
            inn.setLastBowler(bowler);
            inn.setCurrentBowler(null);
            inn.setCurrentOverRuns(0);
        }

        delivery.recordScore(inn.getRuns(), inn.getWickets());
        String text = req.commentary() != null && !req.commentary().isBlank()
                ? req.commentary().trim()
                : commentary.describe(delivery, inn.battingEntryFor(dismissed != null ? dismissed : striker), dismissal);
        delivery.setCommentary(text);
        deliveries.save(delivery);

        boolean oversDone = inn.getLegalBalls() >= match.getOversPerInnings() * 6;
        boolean chased = inn.getTarget() != null && inn.getRuns() >= inn.getTarget();
        if (inn.isAllOut() || oversDone || chased) {
            lifecycle.completeInnings(match, inn);
        }
        match.touch();
        return delivery;
    }

    private void validate(DeliveryRequest req, ExtraType extra) {
        if (extra == ExtraType.NONE && req.extraRuns() > 0) {
            throw new ScoringException("extraRuns only applies to wides, no-balls, byes and leg byes");
        }
        if ((extra == ExtraType.WIDE || extra == ExtraType.BYE || extra == ExtraType.LEG_BYE) && req.runsOffBat() > 0) {
            throw new ScoringException("No runs can be scored off the bat on a " + label(extra));
        }
        if ((extra == ExtraType.BYE || extra == ExtraType.LEG_BYE) && req.extraRuns() == 0 && !req.wicket()) {
            throw new ScoringException("A " + label(extra) + " needs at least one run");
        }
        if (!req.wicket()) {
            return;
        }
        if (req.wicketType() == null) {
            throw new ScoringException("wicketType is required when wicket is true");
        }
        if (extra == ExtraType.WIDE && !ON_WIDE.contains(req.wicketType())) {
            throw new ScoringException("Only run out or stumped is possible off a wide");
        }
        if ((extra == ExtraType.NO_BALL || extra == ExtraType.BYE || extra == ExtraType.LEG_BYE)
                && !ON_OTHER_EXTRAS.contains(req.wicketType())) {
            throw new ScoringException("Only a run out is possible off a " + label(extra));
        }
    }

    private void validateDismissal(DeliveryRequest req, Innings inn) {
        if (!req.wicket() || req.dismissedPlayerId() == null) {
            return;
        }
        Long id = req.dismissedPlayerId();
        if (!id.equals(idOf(inn.getStriker())) && !id.equals(idOf(inn.getNonStriker()))) {
            throw new ScoringException("The dismissed player must be one of the two batters at the crease");
        }
        if (id.equals(idOf(inn.getNonStriker())) && req.wicketType() != WicketType.RUN_OUT) {
            throw new ScoringException("The non-striker can only be run out");
        }
    }

    private Player resolveFielder(DeliveryRequest req, Innings inn, Player bowler) {
        if (req.fielderId() != null) {
            Player fielder = players.findById(req.fielderId()).orElseThrow(() -> new NotFoundException("Player", req.fielderId()));
            if (!fielder.getTeam().getId().equals(inn.getBowlingTeam().getId())) {
                throw new ScoringException(fielder.getName() + " is not in the fielding side");
            }
            return fielder;
        }
        return switch (req.wicketType()) {
            case CAUGHT -> selection.randomFielder(inn, bowler);
            case RUN_OUT -> selection.randomFielder(inn, null);
            case STUMPED -> selection.wicketKeeper(inn);
            default -> null;
        };
    }

    private static String describeDismissal(WicketType type, Player bowler, Player fielder) {
        String b = bowler.getName();
        return switch (type) {
            case BOWLED -> "b " + b;
            case CAUGHT -> same(fielder, bowler) ? "c & b " + b : "c " + fielder.getName() + " b " + b;
            case LBW -> "lbw b " + b;
            case STUMPED -> "st " + fielder.getName() + " b " + b;
            case RUN_OUT -> "run out (" + fielder.getName() + ")";
            case HIT_WICKET -> "hit wicket b " + b;
        };
    }

    private static String label(ExtraType extra) {
        return extra.name().toLowerCase().replace('_', ' ');
    }

    private static Long idOf(Player p) {
        return p == null ? null : p.getId();
    }

    private static boolean same(Player a, Player b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }
}
