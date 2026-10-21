package com.cricpulse;

import com.cricpulse.domain.Player;
import com.cricpulse.domain.PlayerRole;
import com.cricpulse.domain.Team;
import com.cricpulse.repository.PlayerRepository;
import com.cricpulse.repository.TeamRepository;

import java.util.concurrent.atomic.AtomicInteger;

final class TestTeams {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private TestTeams() {
    }

    static Team create(TeamRepository teams, PlayerRepository players) {
        int n = SEQ.incrementAndGet();
        Team team = teams.save(new Team("Test XI " + n, "T" + n, "#123456", "Test Ground " + n));
        for (int i = 1; i <= 11; i++) {
            PlayerRole role = i <= 4 ? PlayerRole.BATTER : i == 5 ? PlayerRole.WICKET_KEEPER
                    : i <= 7 ? PlayerRole.ALL_ROUNDER : PlayerRole.BOWLER;
            players.save(new Player("P" + n + "-" + i, role, team, i, 90 - i * 6, 10 + i * 6,
                    "Right-hand bat", i == 8 ? "Right-arm off-spin" : "Right-arm fast"));
        }
        return team;
    }
}
