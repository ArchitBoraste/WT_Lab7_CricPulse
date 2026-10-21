package com.cricpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cricpulse.simulation")
public class SimulationProperties {

    /** Master switch for the live simulation engine. */
    private boolean enabled = true;
    /** Load demo teams, players and matches on startup. */
    private boolean seedDemoData = true;
    /** Delay between simulation ticks; each live match bowls at most one ball per tick. */
    private long ballIntervalMs = 2500;
    /** Chance that a match skips a tick, so live matches do not move in lock-step. */
    private double skipProbability = 0.2;
    /** Keep at least this many upcoming fixtures scheduled. */
    private int minUpcomingFixtures = 3;
    /** Gap between generated fixtures. */
    private long fixtureGapSeconds = 240;
    /** Threads used to bowl balls for different matches in parallel. */
    private int workerThreads = 4;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isSeedDemoData() { return seedDemoData; }
    public void setSeedDemoData(boolean seedDemoData) { this.seedDemoData = seedDemoData; }
    public long getBallIntervalMs() { return ballIntervalMs; }
    public void setBallIntervalMs(long ballIntervalMs) { this.ballIntervalMs = ballIntervalMs; }
    public double getSkipProbability() { return skipProbability; }
    public void setSkipProbability(double skipProbability) { this.skipProbability = skipProbability; }
    public int getMinUpcomingFixtures() { return minUpcomingFixtures; }
    public void setMinUpcomingFixtures(int minUpcomingFixtures) { this.minUpcomingFixtures = minUpcomingFixtures; }
    public long getFixtureGapSeconds() { return fixtureGapSeconds; }
    public void setFixtureGapSeconds(long fixtureGapSeconds) { this.fixtureGapSeconds = fixtureGapSeconds; }
    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
}
