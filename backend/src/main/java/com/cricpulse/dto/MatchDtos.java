package com.cricpulse.dto;

import com.cricpulse.domain.ExtraType;
import com.cricpulse.domain.MatchFormat;
import com.cricpulse.domain.MatchStatus;
import com.cricpulse.domain.WicketType;

import java.time.Instant;
import java.util.List;

/**
 * Read models pushed over REST and WebSocket. They are immutable snapshots, so a single
 * instance can be cached and served to any number of clients concurrently.
 */
public final class MatchDtos {

    private MatchDtos() {
    }

    public record InningsScoreDto(int number, Long teamId, String teamShort, int runs, int wickets, String overs,
                                  int legalBalls, double runRate, boolean completed, Integer target) {
    }

    public record WinProbabilityDto(Long teamAId, int teamAPercent, Long teamBId, int teamBPercent) {
    }

    public record MatchSummaryDto(Long id, TeamDto teamA, TeamDto teamB, String venue, MatchFormat format,
                                  int oversPerInnings, MatchStatus status, String statusText,
                                  List<InningsScoreDto> innings, Integer currentInnings,
                                  Double currentRunRate, Double requiredRunRate, Integer target,
                                  String result, Long winnerId, String tossText,
                                  Instant startTime, Instant completedAt, Instant lastUpdated,
                                  boolean autoSimulate, boolean paused,
                                  List<String> recentBalls, WinProbabilityDto winProbability) {
    }

    public record BatterDto(Long playerId, String name, int runs, int balls, int fours, int sixes,
                            double strikeRate, boolean out, String dismissal, boolean onStrike, boolean atCrease) {
    }

    public record BowlerDto(Long playerId, String name, String overs, int maidens, int runs, int wickets,
                            double economy, int wides, int noBalls, int dots, boolean bowling) {
    }

    public record FallOfWicketDto(int wicket, int score, String over, Long playerId, String playerName) {
    }

    public record OverSummaryDto(int over, int runs, int wickets, int cumulativeRuns, String bowlerName) {
    }

    public record ExtrasDto(int total, int wides, int noBalls, int byes, int legByes) {
    }

    public record ScorecardDto(int number, TeamDto battingTeam, TeamDto bowlingTeam, int runs, int wickets,
                               String overs, double runRate, Integer target, boolean completed, ExtrasDto extras,
                               List<BatterDto> batting, List<BowlerDto> bowling,
                               List<FallOfWicketDto> fallOfWickets, List<PlayerRefDto> didNotBat,
                               List<OverSummaryDto> overSummaries) {
    }

    public record BallDto(Long id, int inningsNumber, String overLabel, String outcome, int totalRuns,
                          int runsOffBat, ExtraType extraType, boolean wicket, WicketType wicketType,
                          String batter, String bowler, String commentary, String score,
                          boolean endOfOver, Instant timestamp) {
    }

    public record LiveStateDto(int inningsNumber, Long battingTeamId, Long bowlingTeamId,
                               BatterDto striker, BatterDto nonStriker, BowlerDto bowler,
                               int partnershipRuns, int partnershipBalls, List<String> thisOver,
                               String lastWicket, boolean needsBowler, Long lastBowlerId,
                               Integer projectedScore, Integer runsNeeded, Integer ballsRemaining) {
    }

    public record MatchDetailDto(MatchSummaryDto summary, LiveStateDto live, List<ScorecardDto> scorecards,
                                 List<BallDto> recentBalls, PlayerRefDto playerOfMatch) {
    }

    public record DashboardDto(List<MatchSummaryDto> live, List<MatchSummaryDto> upcoming,
                               List<MatchSummaryDto> recent) {
    }
}
