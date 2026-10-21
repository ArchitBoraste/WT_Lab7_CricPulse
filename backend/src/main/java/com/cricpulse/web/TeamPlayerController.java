package com.cricpulse.web;

import com.cricpulse.dto.PlayerDto;
import com.cricpulse.dto.StatsDtos.PlayerProfileDto;
import com.cricpulse.dto.TeamDto;
import com.cricpulse.repository.PlayerRepository;
import com.cricpulse.repository.TeamRepository;
import com.cricpulse.service.NotFoundException;
import com.cricpulse.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Teams & Players")
public class TeamPlayerController {

    private final TeamRepository teams;
    private final PlayerRepository players;
    private final StatsService stats;

    public TeamPlayerController(TeamRepository teams, PlayerRepository players, StatsService stats) {
        this.teams = teams;
        this.players = players;
        this.stats = stats;
    }

    @GetMapping("/teams")
    @Operation(summary = "All teams")
    public List<TeamDto> teams() {
        return teams.findAll().stream().map(TeamDto::of).toList();
    }

    @GetMapping("/teams/{id}")
    public TeamDto team(@PathVariable Long id) {
        return teams.findById(id).map(TeamDto::of).orElseThrow(() -> new NotFoundException("Team", id));
    }

    @GetMapping("/teams/{id}/players")
    @Transactional(readOnly = true)
    @Operation(summary = "Squad of a team in batting order")
    public List<PlayerDto> squad(@PathVariable Long id) {
        if (!teams.existsById(id)) throw new NotFoundException("Team", id);
        return players.findByTeamIdOrderByBattingOrderAsc(id).stream().map(PlayerDto::of).toList();
    }

    @GetMapping("/players")
    @Transactional(readOnly = true)
    @Operation(summary = "Search players by name and/or team")
    public List<PlayerDto> search(@RequestParam(required = false) Long teamId,
                                  @RequestParam(required = false) String q) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return players.search(teamId, query).stream().map(PlayerDto::of).toList();
    }

    @GetMapping("/players/{id}")
    @Operation(summary = "Player profile with career batting and bowling statistics")
    public PlayerProfileDto player(@PathVariable Long id) {
        return stats.profile(id);
    }
}
