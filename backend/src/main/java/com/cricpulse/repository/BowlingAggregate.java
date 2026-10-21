package com.cricpulse.repository;

/** Career bowling totals of one player, produced by a single GROUP BY query. */
public record BowlingAggregate(Long playerId, String name, String team, String teamColor,
                               Long innings, Long balls, Long runs, Long wickets, Long maidens, Long dots,
                               Long threeWicketHauls) {
}
