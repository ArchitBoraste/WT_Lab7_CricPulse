package com.cricpulse.repository;

/** Career batting totals of one player, produced by a single GROUP BY query. */
public record BattingAggregate(Long playerId, String name, String team, String teamColor,
                               Long innings, Long runs, Long balls, Integer highest,
                               Long fours, Long sixes, Long outs, Long fifties, Long hundreds) {
}
