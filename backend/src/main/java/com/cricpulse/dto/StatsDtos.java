package com.cricpulse.dto;

import java.time.Instant;
import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record BattingStatsDto(Long playerId, String name, String team, String teamColor, long innings,
                                  long notOuts, long runs, long balls, int highest, Double average,
                                  double strikeRate, long fours, long sixes, long fifties, long hundreds) {
    }

    public record BowlingStatsDto(Long playerId, String name, String team, String teamColor, long innings,
                                  String overs, long balls, long runs, long wickets, double economy,
                                  Double average, Double strikeRate, long maidens, long dots,
                                  long threeWicketHauls) {
    }

    public record LeaderboardDto(List<BattingStatsDto> mostRuns, List<BowlingStatsDto> mostWickets,
                                 List<BattingStatsDto> bestStrikeRate, List<BattingStatsDto> mostSixes,
                                 List<BowlingStatsDto> bestEconomy) {
    }

    public record OverviewDto(long totalMatches, long liveMatches, long scheduledMatches, long completedMatches,
                              long deliveries, long runs, long fours, long sixes, long wickets) {
    }

    public record RecentPerformanceDto(Long matchId, String opponent, Instant date, String batting,
                                       String bowling) {
    }

    public record PlayerProfileDto(PlayerDto player, long matches, BattingStatsDto batting,
                                   BowlingStatsDto bowling, String bestBowling,
                                   List<RecentPerformanceDto> recent) {
    }
}
