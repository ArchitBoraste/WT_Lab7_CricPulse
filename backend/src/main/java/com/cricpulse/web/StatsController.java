package com.cricpulse.web;

import com.cricpulse.dto.StatsDtos.LeaderboardDto;
import com.cricpulse.dto.StatsDtos.OverviewDto;
import com.cricpulse.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Statistics")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Top run scorers, wicket takers, strike rates, six hitters and economies")
    public LeaderboardDto leaderboard(@RequestParam(defaultValue = "10") int limit) {
        return stats.leaderboard(limit);
    }

    @GetMapping("/overview")
    @Operation(summary = "Platform-wide totals")
    public OverviewDto overview() {
        return stats.overview();
    }
}
