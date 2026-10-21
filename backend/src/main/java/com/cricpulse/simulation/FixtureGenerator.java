package com.cricpulse.simulation;

import com.cricpulse.config.SimulationProperties;
import com.cricpulse.domain.Match;
import com.cricpulse.domain.MatchFormat;
import com.cricpulse.domain.MatchStatus;
import com.cricpulse.domain.Team;
import com.cricpulse.repository.MatchRepository;
import com.cricpulse.repository.TeamRepository;
import com.cricpulse.service.MatchUpdateExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Keeps the fixture list topped up so the platform always has matches coming up. */
@Component
public class FixtureGenerator {

    private final MatchRepository matches;
    private final TeamRepository teams;
    private final MatchUpdateExecutor executor;
    private final SimulationProperties props;

    public FixtureGenerator(MatchRepository matches, TeamRepository teams, MatchUpdateExecutor executor,
                            SimulationProperties props) {
        this.matches = matches;
        this.teams = teams;
        this.executor = executor;
        this.props = props;
    }

    public synchronized void ensureUpcoming() {
        long upcoming = matches.countByStatusAndAutoSimulateTrue(MatchStatus.SCHEDULED);
        for (long i = upcoming; i < props.getMinUpcomingFixtures(); i++) {
            createNext();
        }
    }

    public Long createNext() {
        List<Team> all = new ArrayList<>(teams.findAll());
        if (all.size() < 2) return null;

        Set<Long> busy = new HashSet<>(matches.findBusyTeamAIds());
        busy.addAll(matches.findBusyTeamBIds());
        List<Team> free = all.stream().filter(t -> !busy.contains(t.getId())).toList();
        List<Team> pool = new ArrayList<>(free.size() >= 2 ? free : all);
        Collections.shuffle(pool);
        Team home = pool.get(0);
        Team away = pool.get(1);

        Instant now = Instant.now();
        Instant latest = matches.findLatestScheduledStart();
        Instant base = latest == null || latest.isBefore(now) ? now : latest;
        Instant start = base.plusSeconds(props.getFixtureGapSeconds());

        MatchFormat format = ThreadLocalRandom.current().nextDouble() < 0.3 ? MatchFormat.T10 : MatchFormat.T20;
        Match saved = matches.save(new Match(home, away, home.getHomeGround(), format, start, true));
        executor.refresh(saved.getId());
        return saved.getId();
    }
}
