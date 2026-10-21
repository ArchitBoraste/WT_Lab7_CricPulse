package com.cricpulse.simulation;

import com.cricpulse.config.SimulationProperties;
import com.cricpulse.domain.MatchStatus;
import com.cricpulse.repository.MatchRepository;
import com.cricpulse.service.MatchLifecycle;
import com.cricpulse.service.MatchUpdateExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The "live feed". On every tick it starts fixtures that are due, then bowls one ball in each live
 * match. Matches are processed concurrently on a worker pool; each one is isolated by its own lock
 * inside {@link MatchUpdateExecutor}.
 */
@Component
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final SimulationProperties props;
    private final MatchRepository matches;
    private final MatchUpdateExecutor executor;
    private final MatchLifecycle lifecycle;
    private final MatchSimulator simulator;
    private final FixtureGenerator fixtures;
    private final DataSeeder seeder;
    private final TaskExecutor workers;

    public SimulationEngine(SimulationProperties props, MatchRepository matches, MatchUpdateExecutor executor,
                            MatchLifecycle lifecycle, MatchSimulator simulator, FixtureGenerator fixtures,
                            DataSeeder seeder, @Qualifier("simulationWorkers") TaskExecutor workers) {
        this.props = props;
        this.matches = matches;
        this.executor = executor;
        this.lifecycle = lifecycle;
        this.simulator = simulator;
        this.fixtures = fixtures;
        this.seeder = seeder;
        this.workers = workers;
    }

    @Scheduled(fixedDelayString = "${cricpulse.simulation.ball-interval-ms:2500}", initialDelay = 1000)
    public void tick() {
        if (!props.isEnabled() || !seeder.isReady()) {
            return;
        }
        try {
            for (Long id : matches.findDueScheduledIds(Instant.now())) {
                safely(id, () -> executor.update(id, m -> {
                    if (m.getStatus() == MatchStatus.SCHEDULED) lifecycle.start(m, null, null);
                }));
            }

            List<Long> live = matches.findSimulatableIds(MatchStatus.LIVE);
            CompletableFuture<?>[] jobs = live.stream()
                    .filter(id -> ThreadLocalRandom.current().nextDouble() >= props.getSkipProbability())
                    .map(id -> CompletableFuture.runAsync(() -> safely(id, () -> executor.update(id, m -> {
                        if (m.isAutoSimulate() && !m.isPaused()) simulator.bowlOne(m);
                    })), workers))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(jobs).get(30, TimeUnit.SECONDS);

            if (props.isSeedDemoData()) {
                fixtures.ensureUpcoming();
            }
        } catch (Exception e) {
            log.warn("Simulation tick failed: {}", e.toString());
        }
    }

    private void safely(Long matchId, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.warn("Simulation step for match {} failed: {}", matchId, e.toString());
        }
    }
}
