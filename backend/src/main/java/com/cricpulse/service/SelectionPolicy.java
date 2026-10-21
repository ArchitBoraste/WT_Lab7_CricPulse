package com.cricpulse.service;

import com.cricpulse.domain.*;
import com.cricpulse.repository.PlayerRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Chooses bowlers, incoming batters and fielders when the scorer does not specify them. */
@Component
public class SelectionPolicy {

    private static final int BOWLING_ATTACK_SIZE = 6;

    private final PlayerRepository players;

    public SelectionPolicy(PlayerRepository players) {
        this.players = players;
    }

    /** Each bowler may bowl at most a fifth of the overs (4 in a T20, 10 in an ODI). */
    public int maxBallsPerBowler(Match match) {
        return (int) Math.ceil(match.getOversPerInnings() / 5.0) * 6;
    }

    public Player chooseBowler(Match match, Innings inn) {
        List<Player> squad = players.findByTeamIdOrderByBowlingSkillDesc(inn.getBowlingTeam().getId());
        int quota = maxBallsPerBowler(match);
        Long lastId = inn.getLastBowler() == null ? null : inn.getLastBowler().getId();

        List<Player> candidates = squad.stream().limit(BOWLING_ATTACK_SIZE)
                .filter(p -> !p.getId().equals(lastId) && ballsBowled(inn, p) < quota).toList();
        if (candidates.isEmpty()) {
            candidates = squad.stream().filter(p -> !p.getId().equals(lastId) && ballsBowled(inn, p) < quota).toList();
        }
        if (candidates.isEmpty()) {
            candidates = squad.stream().filter(p -> !p.getId().equals(lastId)).toList();
        }
        if (candidates.isEmpty()) {
            candidates = squad;
        }

        // Weight by skill and by overs still in hand, so quotas get used up evenly.
        double[] weights = new double[candidates.size()];
        double total = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Player p = candidates.get(i);
            int remainingOvers = Math.max(1, (quota - ballsBowled(inn, p)) / 6);
            weights[i] = Math.max(5, p.getBowlingSkill()) * remainingOvers;
            total += weights[i];
        }
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    public Player nextBatter(Innings inn) {
        Set<Long> used = inn.getBatting().stream().map(b -> b.getPlayer().getId()).collect(Collectors.toSet());
        return players.findByTeamIdOrderByBattingOrderAsc(inn.getBattingTeam().getId()).stream()
                .filter(p -> !used.contains(p.getId()))
                .findFirst()
                .orElse(null);
    }

    public List<Player> lineup(Team team) {
        return players.findByTeamIdOrderByBattingOrderAsc(team.getId());
    }

    public Player randomFielder(Innings inn, Player exclude) {
        List<Player> squad = players.findByTeamIdOrderByBattingOrderAsc(inn.getBowlingTeam().getId()).stream()
                .filter(p -> exclude == null || !Objects.equals(p.getId(), exclude.getId()))
                .toList();
        return squad.get(ThreadLocalRandom.current().nextInt(squad.size()));
    }

    public Player wicketKeeper(Innings inn) {
        return players.findByTeamIdOrderByBattingOrderAsc(inn.getBowlingTeam().getId()).stream()
                .filter(p -> p.getRole() == PlayerRole.WICKET_KEEPER)
                .findFirst()
                .orElseGet(() -> randomFielder(inn, null));
    }

    private int ballsBowled(Innings inn, Player p) {
        BowlingEntry e = inn.bowlingEntryFor(p);
        return e == null ? 0 : e.getLegalBalls();
    }
}
