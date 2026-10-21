package com.cricpulse.simulation;

import com.cricpulse.config.SimulationProperties;
import com.cricpulse.domain.*;
import com.cricpulse.repository.MatchRepository;
import com.cricpulse.repository.PlayerRepository;
import com.cricpulse.repository.TeamRepository;
import com.cricpulse.service.MatchLifecycle;
import com.cricpulse.service.MatchUpdateExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Seeds eight fictional franchises with generated squads, then plays out some matches so the
 * platform starts with history: a few completed results, a few matches in progress, and
 * upcoming fixtures.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[][] TEAMS = {
            {"Mumbai Mavericks", "MUM", "#1565C0", "Marine Drive Arena, Mumbai"},
            {"Chennai Chargers", "CHE", "#F9A825", "Bay of Bengal Oval, Chennai"},
            {"Delhi Dynamos", "DEL", "#C62828", "Yamuna Park Stadium, Delhi"},
            {"Kolkata Knights", "KOL", "#6A1B9A", "Hooghly Gardens, Kolkata"},
            {"Bengaluru Blasters", "BLR", "#D84315", "Garden City Ground, Bengaluru"},
            {"Hyderabad Hurricanes", "HYD", "#00897B", "Charminar Oval, Hyderabad"},
            {"Jaipur Jaguars", "JAI", "#D81B60", "Pink City Park, Jaipur"},
            {"Punjab Panthers", "PUN", "#2E7D32", "Five Rivers Stadium, Mohali"},
    };

    private static final String[] FIRST = {
            "Aarav", "Rohan", "Kabir", "Vihaan", "Arjun", "Ishaan", "Dhruv", "Kunal", "Nikhil", "Siddharth",
            "Yash", "Aditya", "Pranav", "Rahul", "Varun", "Manav", "Tejas", "Harsh", "Ayaan", "Jai", "Dev",
            "Rudra", "Neel", "Parth", "Karan", "Samar", "Tanay", "Vikram", "Zaid", "Liam", "Noah", "Ethan",
            "Mason", "Lucas", "Oliver", "Jack", "Ryan", "Sam", "Ben", "Josh", "Tom", "Imran", "Faizan",
            "Kusal", "Dinesh", "Mitchell", "Glen", "Shane", "Asher", "Omar", "Reza", "Tariq", "Hamza", "Kiran"};
    private static final String[] LAST = {
            "Mehta", "Kapoor", "Rao", "Iyer", "Nair", "Kulkarni", "Desai", "Bhat", "Joshi", "Patil", "Reddy",
            "Menon", "Chopra", "Saxena", "Malhotra", "Bose", "Sen", "Ghosh", "Pillai", "Thakur", "Rathore",
            "Chauhan", "Gill", "Sandhu", "Verma", "Mishra", "Pandey", "Shetty", "Hegde", "Kamath", "Walker",
            "Hughes", "Brooks", "Fletcher", "Hayes", "Carter", "Bennett", "Clarke", "Morgan", "Khan", "Ahmed",
            "Perera", "Fernando", "Dsouza", "Banerjee", "Arora", "Bajwa", "Qureshi", "Lobo", "Rizvi"};

    /** Line-up template: role, batting skill, bowling skill, bowling style. */
    private static final Object[][] LINEUP = {
            {PlayerRole.BATTER, 80, 12, "Right-arm medium"},
            {PlayerRole.BATTER, 77, 10, "Right-arm off-spin"},
            {PlayerRole.BATTER, 82, 20, "Right-arm leg-spin"},
            {PlayerRole.BATTER, 76, 15, "Right-arm medium"},
            {PlayerRole.WICKET_KEEPER, 72, 5, null},
            {PlayerRole.ALL_ROUNDER, 68, 62, "Right-arm medium-fast"},
            {PlayerRole.ALL_ROUNDER, 60, 68, "Left-arm orthodox spin"},
            {PlayerRole.BOWLER, 40, 76, "Right-arm off-spin"},
            {PlayerRole.BOWLER, 30, 80, "Left-arm fast"},
            {PlayerRole.BOWLER, 24, 82, "Right-arm fast"},
            {PlayerRole.BOWLER, 18, 84, "Right-arm fast-medium"},
    };

    private final TeamRepository teams;
    private final PlayerRepository players;
    private final MatchRepository matches;
    private final MatchUpdateExecutor executor;
    private final MatchLifecycle lifecycle;
    private final MatchSimulator simulator;
    private final FixtureGenerator fixtures;
    private final SimulationProperties props;

    private volatile boolean ready;

    public DataSeeder(TeamRepository teams, PlayerRepository players, MatchRepository matches,
                      MatchUpdateExecutor executor, MatchLifecycle lifecycle, MatchSimulator simulator,
                      FixtureGenerator fixtures, SimulationProperties props) {
        this.teams = teams;
        this.players = players;
        this.matches = matches;
        this.executor = executor;
        this.lifecycle = lifecycle;
        this.simulator = simulator;
        this.fixtures = fixtures;
        this.props = props;
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (props.isSeedDemoData() && teams.count() == 0) {
                long t0 = System.currentTimeMillis();
                seedSquads();
                List<Team> all = teams.findAll();
                Random rnd = new Random();
                Instant now = Instant.now();

                // Completed matches over the past few days.
                for (int i = 0; i < 4; i++) {
                    Team a = all.get((2 * i) % all.size());
                    Team b = all.get((2 * i + 3) % all.size());
                    Instant start = now.minus(Duration.ofHours(20L * (4 - i) + 3));
                    playMatch(a, b, MatchFormat.T20, start, Integer.MAX_VALUE, true);
                }
                // Matches in progress at different stages.
                playMatch(all.get(0), all.get(1), MatchFormat.T20, now.minusSeconds(3600), 40 + rnd.nextInt(50), false);
                playMatch(all.get(2), all.get(3), MatchFormat.T20, now.minusSeconds(5400), 150 + rnd.nextInt(40), false);
                playMatch(all.get(4), all.get(5), MatchFormat.T10, now.minusSeconds(900), 8 + rnd.nextInt(20), false);

                fixtures.ensureUpcoming();
                log.info("Seeded {} teams, {} players and {} matches in {} ms", all.size(), players.count(),
                        matches.count(), System.currentTimeMillis() - t0);
            }
            // Warm the snapshot cache so the first requests are served from memory.
            matches.findAllIds().forEach(executor::snapshot);
        } finally {
            ready = true;
        }
    }

    private void seedSquads() {
        Random rnd = new Random(2026);
        Set<String> used = new HashSet<>();
        for (String[] t : TEAMS) {
            Team team = teams.save(new Team(t[0], t[1], t[2], t[3]));
            for (int i = 0; i < LINEUP.length; i++) {
                Object[] slot = LINEUP[i];
                String name;
                do {
                    name = FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
                } while (!used.add(name));
                int bat = clamp((int) slot[1] + rnd.nextInt(15) - 7);
                int bowl = clamp((int) slot[2] + rnd.nextInt(15) - 7);
                String batStyle = rnd.nextDouble() < 0.3 ? "Left-hand bat" : "Right-hand bat";
                players.save(new Player(name, (PlayerRole) slot[0], team, i + 1, bat, bowl, batStyle, (String) slot[3]));
            }
        }
    }

    private void playMatch(Team a, Team b, MatchFormat format, Instant start, int balls, boolean complete) {
        Match saved = matches.save(new Match(a, b, a.getHomeGround(), format, start, true));
        executor.update(saved.getId(), m -> {
            lifecycle.start(m, null, null);
            m.setStartTime(start);
            simulator.fastForward(m, balls);
            if (complete && m.getStatus() == MatchStatus.COMPLETED) {
                m.setCompletedAt(start.plus(Duration.ofMinutes(format == MatchFormat.T10 ? 95 : 200)));
            }
        });
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(99, v));
    }
}
