package com.cricpulse.service;

import com.cricpulse.domain.BattingEntry;
import com.cricpulse.domain.BowlingEntry;
import com.cricpulse.domain.Innings;
import com.cricpulse.domain.MatchStatus;
import com.cricpulse.domain.Player;
import com.cricpulse.dto.PlayerDto;
import com.cricpulse.dto.StatsDtos.*;
import com.cricpulse.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Career statistics aggregated in the database with GROUP BY queries. The leaderboard changes on
 * every ball, so it is cached for a few seconds: enough to absorb heavy read traffic while still
 * looking live.
 */
@Service
public class StatsService {

    private static final Duration TTL = Duration.ofSeconds(5);
    private static final int MIN_BALLS_FOR_STRIKE_RATE = 30;
    private static final int MIN_BALLS_FOR_ECONOMY = 36;

    private final BattingEntryRepository batting;
    private final BowlingEntryRepository bowling;
    private final PlayerRepository players;
    private final MatchRepository matches;
    private final DeliveryRepository deliveries;

    private volatile Cached<LeaderboardDto> leaderboard;
    private volatile Cached<OverviewDto> overview;

    public StatsService(BattingEntryRepository batting, BowlingEntryRepository bowling, PlayerRepository players,
                        MatchRepository matches, DeliveryRepository deliveries) {
        this.batting = batting;
        this.bowling = bowling;
        this.players = players;
        this.matches = matches;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public LeaderboardDto leaderboard(int limit) {
        LeaderboardDto full = cached(() -> leaderboard, c -> leaderboard = c, this::computeLeaderboard);
        return new LeaderboardDto(top(full.mostRuns(), limit), top(full.mostWickets(), limit),
                top(full.bestStrikeRate(), limit), top(full.mostSixes(), limit), top(full.bestEconomy(), limit));
    }

    @Transactional(readOnly = true)
    public OverviewDto overview() {
        return cached(() -> overview, c -> overview = c, () -> {
            DeliveryTotals t = deliveries.totals();
            return new OverviewDto(matches.count(), matches.countByStatus(MatchStatus.LIVE),
                    matches.countByStatus(MatchStatus.SCHEDULED), matches.countByStatus(MatchStatus.COMPLETED),
                    t.deliveries(), t.runs(), t.fours(), t.sixes(), t.wickets());
        });
    }

    @Transactional(readOnly = true)
    public PlayerProfileDto profile(Long playerId) {
        Player p = players.findById(playerId).orElseThrow(() -> new NotFoundException("Player", playerId));
        List<BattingEntry> bats = batting.findByPlayerIdOrderByIdDesc(playerId);
        List<BowlingEntry> bowls = bowling.findByPlayerIdOrderByIdDesc(playerId);

        BattingStatsDto batStats = null;
        if (!bats.isEmpty()) {
            long runs = 0, balls = 0, fours = 0, sixes = 0, outs = 0, fifties = 0, hundreds = 0;
            int highest = 0;
            for (BattingEntry b : bats) {
                runs += b.getRuns();
                balls += b.getBalls();
                fours += b.getFours();
                sixes += b.getSixes();
                if (b.isOut()) outs++;
                if (b.getRuns() >= 100) hundreds++;
                else if (b.getRuns() >= 50) fifties++;
                highest = Math.max(highest, b.getRuns());
            }
            batStats = battingStats(new BattingAggregate(p.getId(), p.getName(), p.getTeam().getShortName(),
                    p.getTeam().getColor(), (long) bats.size(), runs, balls, highest, fours, sixes, outs, fifties, hundreds));
        }

        BowlingStatsDto bowlStats = null;
        String best = null;
        if (!bowls.isEmpty()) {
            long balls = 0, runs = 0, wickets = 0, maidens = 0, dots = 0, threes = 0;
            BowlingEntry bestEntry = null;
            for (BowlingEntry b : bowls) {
                balls += b.getLegalBalls();
                runs += b.getRuns();
                wickets += b.getWickets();
                maidens += b.getMaidens();
                dots += b.getDots();
                if (b.getWickets() >= 3) threes++;
                if (bestEntry == null || b.getWickets() > bestEntry.getWickets()
                        || (b.getWickets() == bestEntry.getWickets() && b.getRuns() < bestEntry.getRuns())) {
                    bestEntry = b;
                }
            }
            bowlStats = bowlingStats(new BowlingAggregate(p.getId(), p.getName(), p.getTeam().getShortName(),
                    p.getTeam().getColor(), (long) bowls.size(), balls, runs, wickets, maidens, dots, threes));
            best = bestEntry.getWickets() + "/" + bestEntry.getRuns();
        }

        // Merge batting and bowling into one line per innings, newest first.
        Map<Long, String[]> byInnings = new LinkedHashMap<>();
        Map<Long, Innings> inningsById = new HashMap<>();
        Set<Long> matchIds = new HashSet<>();
        for (BattingEntry b : bats) {
            inningsById.put(b.getInnings().getId(), b.getInnings());
            byInnings.computeIfAbsent(b.getInnings().getId(), k -> new String[2])[0] =
                    b.getRuns() + (b.isOut() ? "" : "*") + " (" + b.getBalls() + ")";
            matchIds.add(b.getInnings().getMatch().getId());
        }
        Map<Long, String> bowlingByMatch = new HashMap<>();
        for (BowlingEntry b : bowls) {
            bowlingByMatch.put(b.getInnings().getMatch().getId(), b.getWickets() + "/" + b.getRuns()
                    + " (" + SnapshotBuilder.overs(b.getLegalBalls()) + ")");
            matchIds.add(b.getInnings().getMatch().getId());
        }

        List<RecentPerformanceDto> recent = new ArrayList<>();
        Set<Long> seenMatches = new HashSet<>();
        for (Map.Entry<Long, String[]> e : byInnings.entrySet()) {
            Innings inn = inningsById.get(e.getKey());
            Long matchId = inn.getMatch().getId();
            seenMatches.add(matchId);
            recent.add(new RecentPerformanceDto(matchId, inn.getBowlingTeam().getShortName(),
                    inn.getMatch().getStartTime(), e.getValue()[0], bowlingByMatch.get(matchId)));
        }
        for (BowlingEntry b : bowls) {
            Long matchId = b.getInnings().getMatch().getId();
            if (seenMatches.add(matchId)) {
                recent.add(new RecentPerformanceDto(matchId, b.getInnings().getBattingTeam().getShortName(),
                        b.getInnings().getMatch().getStartTime(), null, bowlingByMatch.get(matchId)));
            }
        }
        recent.sort(Comparator.comparing(RecentPerformanceDto::matchId).reversed());

        return new PlayerProfileDto(PlayerDto.of(p), matchIds.size(), batStats, bowlStats, best,
                recent.stream().limit(10).toList());
    }

    private LeaderboardDto computeLeaderboard() {
        List<BattingStatsDto> bat = batting.aggregateAll().stream().map(StatsService::battingStats).toList();
        List<BowlingStatsDto> bowl = bowling.aggregateAll().stream().map(StatsService::bowlingStats).toList();

        List<BattingStatsDto> mostRuns = bat.stream()
                .sorted(Comparator.comparingLong(BattingStatsDto::runs).reversed()).toList();
        List<BattingStatsDto> bestSr = bat.stream().filter(b -> b.balls() >= MIN_BALLS_FOR_STRIKE_RATE)
                .sorted(Comparator.comparingDouble(BattingStatsDto::strikeRate).reversed()).toList();
        List<BattingStatsDto> mostSixes = bat.stream()
                .sorted(Comparator.comparingLong(BattingStatsDto::sixes).reversed()
                        .thenComparing(Comparator.comparingLong(BattingStatsDto::runs).reversed())).toList();
        List<BowlingStatsDto> mostWickets = bowl.stream()
                .sorted(Comparator.comparingLong(BowlingStatsDto::wickets).reversed()
                        .thenComparingDouble(BowlingStatsDto::economy)).toList();
        List<BowlingStatsDto> bestEcon = bowl.stream().filter(b -> b.balls() >= MIN_BALLS_FOR_ECONOMY)
                .sorted(Comparator.comparingDouble(BowlingStatsDto::economy)).toList();
        return new LeaderboardDto(mostRuns, mostWickets, bestSr, mostSixes, bestEcon);
    }

    private static BattingStatsDto battingStats(BattingAggregate a) {
        long notOuts = a.innings() - a.outs();
        return new BattingStatsDto(a.playerId(), a.name(), a.team(), a.teamColor(), a.innings(), notOuts,
                a.runs(), a.balls(), a.highest() == null ? 0 : a.highest(),
                a.outs() == 0 ? null : round2((double) a.runs() / a.outs()),
                a.balls() == 0 ? 0 : round2(a.runs() * 100.0 / a.balls()),
                a.fours(), a.sixes(), a.fifties(), a.hundreds());
    }

    private static BowlingStatsDto bowlingStats(BowlingAggregate a) {
        return new BowlingStatsDto(a.playerId(), a.name(), a.team(), a.teamColor(), a.innings(),
                SnapshotBuilder.overs(a.balls().intValue()), a.balls(), a.runs(), a.wickets(),
                a.balls() == 0 ? 0 : round2(a.runs() * 6.0 / a.balls()),
                a.wickets() == 0 ? null : round2((double) a.runs() / a.wickets()),
                a.wickets() == 0 ? null : round2((double) a.balls() / a.wickets()),
                a.maidens(), a.dots(), a.threeWicketHauls());
    }

    private static <T> List<T> top(List<T> list, int limit) {
        return list.stream().limit(Math.max(1, Math.min(limit, 50))).toList();
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static <T> T cached(Supplier<Cached<T>> read, java.util.function.Consumer<Cached<T>> write, Supplier<T> compute) {
        Cached<T> c = read.get();
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) {
            return c.value();
        }
        T value = compute.get();
        write.accept(new Cached<>(value, Instant.now()));
        return value;
    }

    private record Cached<T>(T value, Instant at) {
    }
}
