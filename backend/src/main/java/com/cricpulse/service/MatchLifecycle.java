package com.cricpulse.service;

import com.cricpulse.domain.*;
import com.cricpulse.repository.InningsRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Toss, innings transitions and the final result. Must be called inside a transaction. */
@Component
public class MatchLifecycle {

    private final InningsRepository inningsRepository;
    private final SelectionPolicy selection;

    public MatchLifecycle(InningsRepository inningsRepository, SelectionPolicy selection) {
        this.inningsRepository = inningsRepository;
        this.selection = selection;
    }

    public void start(Match match, Long tossWinnerId, TossDecision decision) {
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ScoringException("Match " + match.getId() + " has already started");
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Team tossWinner;
        if (tossWinnerId == null) {
            tossWinner = rnd.nextBoolean() ? match.getTeamA() : match.getTeamB();
        } else if (tossWinnerId.equals(match.getTeamA().getId())) {
            tossWinner = match.getTeamA();
        } else if (tossWinnerId.equals(match.getTeamB().getId())) {
            tossWinner = match.getTeamB();
        } else {
            throw new ScoringException("Toss winner must be one of the two teams");
        }
        TossDecision choice = decision != null ? decision : (rnd.nextDouble() < 0.6 ? TossDecision.BOWL : TossDecision.BAT);
        match.setTossWinner(tossWinner);
        match.setTossDecision(choice);

        Team battingFirst = choice == TossDecision.BAT ? tossWinner : match.opponentOf(tossWinner);
        openInnings(match, 1, battingFirst, match.opponentOf(battingFirst), null);

        match.setStatus(MatchStatus.LIVE);
        Instant now = Instant.now();
        if (match.getStartTime() == null || match.getStartTime().isAfter(now)) {
            match.setStartTime(now);
        }
        match.touch();
    }

    private void openInnings(Match match, int number, Team batting, Team bowling, Integer target) {
        List<Player> lineup = selection.lineup(batting);
        if (lineup.size() < 2) {
            throw new ScoringException(batting.getName() + " needs at least two players to bat");
        }
        Innings inn = new Innings(match, number, batting, bowling, target);
        inn.setMaxWickets(Math.min(10, lineup.size() - 1));
        inn.getBatting().add(new BattingEntry(inn, lineup.get(0), 1));
        inn.getBatting().add(new BattingEntry(inn, lineup.get(1), 2));
        inn.setStriker(lineup.get(0));
        inn.setNonStriker(lineup.get(1));
        match.getInnings().add(inn);
        // Persist now: IDENTITY ids are needed by the deliveries that follow in the same transaction.
        inningsRepository.save(inn);
    }

    public void completeInnings(Match match, Innings inn) {
        inn.setCompleted(true);
        inn.setCurrentBowler(null);
        if (inn.getNumber() == 1) {
            openInnings(match, 2, inn.getBowlingTeam(), inn.getBattingTeam(), inn.getRuns() + 1);
        } else {
            finish(match);
        }
    }

    private void finish(Match match) {
        Innings first = match.getInnings().get(0);
        Innings second = match.getInnings().get(1);
        if (second.getRuns() >= second.getTarget()) {
            int wicketsLeft = second.getMaxWickets() - second.getWickets();
            int ballsLeft = match.getOversPerInnings() * 6 - second.getLegalBalls();
            match.setWinner(second.getBattingTeam());
            match.setResult(second.getBattingTeam().getName() + " won by " + plural(wicketsLeft, "wicket")
                    + (ballsLeft > 0 ? " (" + plural(ballsLeft, "ball") + " left)" : ""));
        } else if (second.getRuns() == first.getRuns()) {
            match.setWinner(null);
            match.setResult("Match tied");
        } else {
            match.setWinner(first.getBattingTeam());
            match.setResult(first.getBattingTeam().getName() + " won by "
                    + plural(first.getRuns() - second.getRuns(), "run"));
        }
        match.setStatus(MatchStatus.COMPLETED);
        match.setCompletedAt(Instant.now());
        match.setPlayerOfMatch(pickPlayerOfMatch(match));
        match.touch();
    }

    /** Simple impact score: runs and tempo for batters, wickets and economy for bowlers. */
    Player pickPlayerOfMatch(Match match) {
        Map<Long, Double> impact = new HashMap<>();
        Map<Long, Player> byId = new HashMap<>();
        for (Innings inn : match.getInnings()) {
            for (BattingEntry b : inn.getBatting()) {
                double score = b.getRuns() + b.getFours() * 0.5 + b.getSixes() + (b.getRuns() >= 50 ? 10 : 0);
                if (b.getBalls() >= 10) {
                    score += (b.getRuns() * 100.0 / b.getBalls() - 130) / 10;
                }
                impact.merge(b.getPlayer().getId(), score, Double::sum);
                byId.put(b.getPlayer().getId(), b.getPlayer());
            }
            for (BowlingEntry b : inn.getBowling()) {
                double score = b.getWickets() * 20 + b.getMaidens() * 5 + b.getDots() * 0.5;
                if (b.getLegalBalls() >= 12) {
                    score += (7.5 - b.getRuns() * 6.0 / b.getLegalBalls()) * 4;
                }
                impact.merge(b.getPlayer().getId(), score, Double::sum);
                byId.put(b.getPlayer().getId(), b.getPlayer());
            }
        }
        Long winnerId = match.getWinner() == null ? null : match.getWinner().getId();
        return impact.entrySet().stream()
                .max((x, y) -> Double.compare(weighted(x.getValue(), byId.get(x.getKey()), winnerId),
                        weighted(y.getValue(), byId.get(y.getKey()), winnerId)))
                .map(e -> byId.get(e.getKey()))
                .orElse(null);
    }

    private static double weighted(double score, Player p, Long winnerId) {
        return winnerId != null && p.getTeam().getId().equals(winnerId) ? score * 1.25 : score;
    }

    static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
