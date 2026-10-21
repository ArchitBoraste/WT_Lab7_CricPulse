package com.cricpulse.service;

import com.cricpulse.domain.*;
import com.cricpulse.dto.MatchDtos.*;
import com.cricpulse.dto.PlayerRefDto;
import com.cricpulse.dto.TeamDto;
import com.cricpulse.repository.DeliveryRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/** Builds the immutable read models (scorecards, live state, summaries) from the domain model. */
@Component
public class SnapshotBuilder {

    private static final int RECENT_BALLS = 30;

    private final DeliveryRepository deliveries;
    private final SelectionPolicy selection;

    public SnapshotBuilder(DeliveryRepository deliveries, SelectionPolicy selection) {
        this.deliveries = deliveries;
        this.selection = selection;
    }

    public MatchDetailDto build(Match m) {
        Map<Integer, List<Delivery>> balls = new LinkedHashMap<>();
        for (Innings inn : m.getInnings()) {
            balls.put(inn.getNumber(), deliveries.findByInningsIdOrderBySequenceAsc(inn.getId()));
        }

        List<ScorecardDto> cards = m.getInnings().stream()
                .map(inn -> scorecard(inn, balls.get(inn.getNumber())))
                .toList();

        List<Delivery> all = balls.values().stream().flatMap(List::stream).toList();
        List<BallDto> recent = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && recent.size() < RECENT_BALLS; i--) {
            recent.add(ball(all.get(i)));
        }

        Innings current = m.getCurrentInnings();
        LiveStateDto live = m.getStatus() == MatchStatus.LIVE && current != null
                ? live(m, current, balls.get(current.getNumber()))
                : null;

        return new MatchDetailDto(summary(m, balls), live, cards, recent, PlayerRefDto.of(m.getPlayerOfMatch()));
    }

    // ---------------------------------------------------------------- summary

    private MatchSummaryDto summary(Match m, Map<Integer, List<Delivery>> balls) {
        List<InningsScoreDto> scores = m.getInnings().stream()
                .map(inn -> new InningsScoreDto(inn.getNumber(), inn.getBattingTeam().getId(),
                        inn.getBattingTeam().getShortName(), inn.getRuns(), inn.getWickets(),
                        overs(inn.getLegalBalls()), inn.getLegalBalls(), rate(inn.getRuns(), inn.getLegalBalls()),
                        inn.isCompleted(), inn.getTarget()))
                .toList();

        Innings current = m.getStatus() == MatchStatus.LIVE ? m.getCurrentInnings() : null;
        Double crr = current == null ? null : rate(current.getRuns(), current.getLegalBalls());
        Double rrr = null;
        Integer target = null;
        if (current != null && current.getTarget() != null) {
            target = current.getTarget();
            int ballsLeft = ballsRemaining(m, current);
            rrr = ballsLeft > 0 ? round2(Math.max(0, target - current.getRuns()) * 6.0 / ballsLeft) : null;
        } else if (m.getInnings().size() == 2) {
            target = m.getInnings().get(1).getTarget();
        }

        Innings latest = m.getInnings().isEmpty() ? null : m.getInnings().get(m.getInnings().size() - 1);
        List<String> lastSix = new ArrayList<>();
        if (latest != null) {
            List<Delivery> list = balls.get(latest.getNumber());
            for (int i = Math.max(0, list.size() - 6); i < list.size(); i++) {
                lastSix.add(outcome(list.get(i)));
            }
        }

        return new MatchSummaryDto(m.getId(), TeamDto.of(m.getTeamA()), TeamDto.of(m.getTeamB()), m.getVenue(),
                m.getFormat(), m.getOversPerInnings(), m.getStatus(), statusText(m, current), scores,
                current == null ? null : current.getNumber(), crr, rrr, target, m.getResult(),
                m.getWinner() == null ? null : m.getWinner().getId(), tossText(m),
                m.getStartTime(), m.getCompletedAt(), m.getLastUpdated(), m.isAutoSimulate(), m.isPaused(),
                lastSix, winProbability(m, current));
    }

    private String statusText(Match m, Innings current) {
        return switch (m.getStatus()) {
            case SCHEDULED -> "Match yet to begin";
            case COMPLETED -> m.getResult();
            case LIVE -> {
                if (current == null) yield "Live";
                if (current.getTarget() != null) {
                    int need = current.getTarget() - current.getRuns();
                    int ballsLeft = ballsRemaining(m, current);
                    yield current.getBattingTeam().getName() + " need " + MatchLifecycle.plural(need, "run")
                            + " from " + MatchLifecycle.plural(ballsLeft, "ball");
                }
                if (current.getLegalBalls() < 6) yield tossText(m);
                yield current.getBattingTeam().getName() + " batting · projected " + projected(m, current);
            }
        };
    }

    private static String tossText(Match m) {
        if (m.getTossWinner() == null) return null;
        return m.getTossWinner().getName() + " won the toss and chose to "
                + (m.getTossDecision() == TossDecision.BAT ? "bat" : "bowl");
    }

    /**
     * A transparent heuristic, not a trained model: compares the runs a side can expect from its
     * remaining resources (balls and wickets) with what it needs.
     */
    private WinProbabilityDto winProbability(Match m, Innings current) {
        Long a = m.getTeamA().getId();
        Long b = m.getTeamB().getId();
        if (m.getStatus() == MatchStatus.COMPLETED) {
            if (m.getWinner() == null) return new WinProbabilityDto(a, 50, b, 50);
            boolean aWon = m.getWinner().getId().equals(a);
            return new WinProbabilityDto(a, aWon ? 100 : 0, b, aWon ? 0 : 100);
        }
        if (current == null) return null;

        double par = parRate(m);
        int totalBalls = m.getOversPerInnings() * 6;
        int ballsLeft = ballsRemaining(m, current);
        double pBatting;
        if (current.getTarget() == null) {
            double parTotal = par * m.getOversPerInnings();
            // Less certainty early in the innings.
            double sd = parTotal * (0.10 + 0.08 * ballsLeft / totalBalls);
            pBatting = logistic((projected(m, current) - parTotal) / sd);
        } else {
            int need = current.getTarget() - current.getRuns();
            if (need <= 0) pBatting = 1;
            else if (ballsLeft <= 0 || current.isAllOut()) pBatting = 0;
            else {
                double wicketsInHand = Math.max(0, current.getMaxWickets() - current.getWickets()) / 10.0;
                double expected = ballsLeft / 6.0 * par * 1.12 * Math.pow(wicketsInHand, 0.4);
                double sd = Math.max(6, Math.sqrt(ballsLeft) * 2.4);
                pBatting = logistic(1.7 * (expected - need) / sd);
            }
        }
        int battingPct = (int) Math.round(Math.min(0.98, Math.max(0.02, pBatting)) * 100);
        boolean aBatting = current.getBattingTeam().getId().equals(a);
        int aPct = aBatting ? battingPct : 100 - battingPct;
        return new WinProbabilityDto(a, aPct, b, 100 - aPct);
    }

    private int projected(Match m, Innings inn) {
        double par = parRate(m);
        int ballsLeft = ballsRemaining(m, inn);
        double crr = inn.getLegalBalls() == 0 ? par : inn.getRuns() * 6.0 / inn.getLegalBalls();
        // Never fully trust the current rate: blend it with par (more so early on) and scale by wickets in hand.
        double weight = Math.min(1, inn.getLegalBalls() / 36.0) * 0.6;
        double rate = weight * crr + (1 - weight) * par;
        double wicketResource = Math.pow(Math.max(0, inn.getMaxWickets() - inn.getWickets()) / 10.0, 0.45);
        return (int) Math.round(inn.getRuns() + ballsLeft / 6.0 * rate * (0.5 + 0.5 * wicketResource));
    }

    /** Typical scoring rate of the format in this simulation. */
    private static double parRate(Match m) {
        return switch (m.getFormat()) {
            case T10 -> 10.5;
            case T20 -> 8.4;
            case ODI -> 5.6;
        };
    }

    // ---------------------------------------------------------------- live state

    private LiveStateDto live(Match m, Innings inn, List<Delivery> balls) {
        BatterDto striker = batter(inn, inn.battingEntryFor(inn.getStriker()));
        BatterDto nonStriker = batter(inn, inn.battingEntryFor(inn.getNonStriker()));
        Player bowlerPlayer = inn.getCurrentBowler() != null ? inn.getCurrentBowler() : inn.getLastBowler();
        BowlerDto bowler = bowlerPlayer == null ? null : bowler(inn, inn.bowlingEntryFor(bowlerPlayer));

        int overIdx = inn.getCurrentBowler() != null ? inn.getLegalBalls() / 6 : Math.max(0, inn.getLegalBalls() / 6 - 1);
        List<String> thisOver = balls.stream().filter(d -> d.getOverNumber() == overIdx).map(SnapshotBuilder::outcome).toList();

        String lastWicket = null;
        for (int i = balls.size() - 1; i >= 0; i--) {
            Delivery d = balls.get(i);
            if (d.isWicket()) {
                BattingEntry e = inn.battingEntryFor(d.getDismissedPlayer());
                lastWicket = d.getDismissedPlayer().getName() + " " + e.getDismissal() + " " + e.getRuns()
                        + " (" + e.getBalls() + ")";
                break;
            }
        }

        boolean chasing = inn.getTarget() != null;
        return new LiveStateDto(inn.getNumber(), inn.getBattingTeam().getId(), inn.getBowlingTeam().getId(),
                striker, nonStriker, bowler, inn.getPartnershipRuns(), inn.getPartnershipBalls(), thisOver,
                lastWicket, inn.getCurrentBowler() == null,
                inn.getLastBowler() == null ? null : inn.getLastBowler().getId(),
                chasing ? null : projected(m, inn),
                chasing ? Math.max(0, inn.getTarget() - inn.getRuns()) : null,
                chasing ? ballsRemaining(m, inn) : null);
    }

    // ---------------------------------------------------------------- scorecard

    private ScorecardDto scorecard(Innings inn, List<Delivery> balls) {
        List<BatterDto> batting = inn.getBatting().stream().map(b -> batter(inn, b)).toList();
        List<BowlerDto> bowling = inn.getBowling().stream().map(b -> bowler(inn, b)).toList();

        List<FallOfWicketDto> fow = balls.stream().filter(Delivery::isWicket)
                .map(d -> new FallOfWicketDto(d.getWicketsAfter(), d.getScoreAfter(), overLabel(d),
                        d.getDismissedPlayer().getId(), d.getDismissedPlayer().getName()))
                .toList();

        Set<Long> batted = new HashSet<>();
        inn.getBatting().forEach(b -> batted.add(b.getPlayer().getId()));
        List<PlayerRefDto> didNotBat = selection.lineup(inn.getBattingTeam()).stream()
                .filter(p -> !batted.contains(p.getId()))
                .map(PlayerRefDto::of)
                .toList();

        List<OverSummaryDto> overs = new ArrayList<>();
        int cumulative = 0;
        int i = 0;
        while (i < balls.size()) {
            int over = balls.get(i).getOverNumber();
            String bowlerName = balls.get(i).getBowler().getName();
            int runs = 0;
            int wkts = 0;
            while (i < balls.size() && balls.get(i).getOverNumber() == over) {
                runs += balls.get(i).getTotalRuns();
                if (balls.get(i).isWicket()) wkts++;
                i++;
            }
            cumulative += runs;
            overs.add(new OverSummaryDto(over + 1, runs, wkts, cumulative, bowlerName));
        }

        return new ScorecardDto(inn.getNumber(), TeamDto.of(inn.getBattingTeam()), TeamDto.of(inn.getBowlingTeam()),
                inn.getRuns(), inn.getWickets(), overs(inn.getLegalBalls()), rate(inn.getRuns(), inn.getLegalBalls()),
                inn.getTarget(), inn.isCompleted(),
                new ExtrasDto(inn.getExtras(), inn.getWides(), inn.getNoBalls(), inn.getByes(), inn.getLegByes()),
                batting, bowling, fow, didNotBat, overs);
    }

    private static BatterDto batter(Innings inn, BattingEntry b) {
        if (b == null) return null;
        Long id = b.getPlayer().getId();
        boolean atCrease = !inn.isCompleted() && !b.isOut()
                && (id.equals(idOf(inn.getStriker())) || id.equals(idOf(inn.getNonStriker())));
        boolean onStrike = atCrease && id.equals(idOf(inn.getStriker()));
        return new BatterDto(id, b.getPlayer().getName(), b.getRuns(), b.getBalls(), b.getFours(), b.getSixes(),
                b.getBalls() == 0 ? 0 : round2(b.getRuns() * 100.0 / b.getBalls()), b.isOut(),
                b.isOut() ? b.getDismissal() : "not out", onStrike, atCrease);
    }

    private static BowlerDto bowler(Innings inn, BowlingEntry b) {
        if (b == null) return null;
        boolean bowling = !inn.isCompleted() && inn.getCurrentBowler() != null
                && inn.getCurrentBowler().getId().equals(b.getPlayer().getId());
        return new BowlerDto(b.getPlayer().getId(), b.getPlayer().getName(), overs(b.getLegalBalls()), b.getMaidens(),
                b.getRuns(), b.getWickets(), rate(b.getRuns(), b.getLegalBalls()), b.getWides(), b.getNoBalls(),
                b.getDots(), bowling);
    }

    // ---------------------------------------------------------------- balls

    public static BallDto ball(Delivery d) {
        return new BallDto(d.getId(), d.getInnings().getNumber(), overLabel(d), outcome(d), d.getTotalRuns(),
                d.getRunsOffBat(), d.getExtraType(), d.isWicket(), d.getWicketType(), d.getBatter().getName(),
                d.getBowler().getName(), d.getCommentary(), d.getScoreAfter() + "/" + d.getWicketsAfter(),
                d.isLegal() && d.getBallInOver() == 6, d.getCreatedAt());
    }

    /** Short label used for the ball-by-ball chips: 0, 1, 4, 6, W, Wd, 2Wd, Nb, 4Nb, 1B, 1Lb. */
    public static String outcome(Delivery d) {
        if (d.isWicket()) return "W";
        int total = d.getTotalRuns();
        return switch (d.getExtraType()) {
            case WIDE -> total == 1 ? "Wd" : total + "Wd";
            case NO_BALL -> total == 1 ? "Nb" : (total - 1) + "Nb";
            case BYE -> total + "B";
            case LEG_BYE -> total + "Lb";
            case NONE -> String.valueOf(d.getRunsOffBat());
        };
    }

    private static String overLabel(Delivery d) {
        return d.getOverNumber() + "." + d.getBallInOver();
    }

    // ---------------------------------------------------------------- helpers

    private static int ballsRemaining(Match m, Innings inn) {
        return Math.max(0, m.getOversPerInnings() * 6 - inn.getLegalBalls());
    }

    public static String overs(int legalBalls) {
        return legalBalls / 6 + "." + legalBalls % 6;
    }

    private static double rate(int runs, int legalBalls) {
        return legalBalls == 0 ? 0 : round2(runs * 6.0 / legalBalls);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double logistic(double x) {
        return 1 / (1 + Math.exp(-x));
    }

    private static Long idOf(Player p) {
        return p == null ? null : p.getId();
    }
}
