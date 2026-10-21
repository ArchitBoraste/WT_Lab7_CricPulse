package com.cricpulse.web;

import com.cricpulse.domain.MatchStatus;
import com.cricpulse.dto.MatchDtos.*;
import com.cricpulse.dto.PageDto;
import com.cricpulse.dto.Requests.*;
import com.cricpulse.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@Tag(name = "Matches", description = "Fixtures, live scores, scorecards, commentary and scoring")
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List matches, optionally filtered by status")
    public List<MatchSummaryDto> list(@RequestParam(required = false) MatchStatus status,
                                      @RequestParam(defaultValue = "20") int limit) {
        return service.list(status, limit);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Live, upcoming and recently completed matches in one call")
    public DashboardDto dashboard(@RequestParam(defaultValue = "8") int limit) {
        return service.dashboard(limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full match centre snapshot: summary, live state, scorecards and recent balls")
    public MatchDetailDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Compact live score of one match")
    public MatchSummaryDto summary(@PathVariable Long id) {
        return service.detail(id).summary();
    }

    @GetMapping("/{id}/scorecard")
    @Operation(summary = "Batting and bowling scorecards of each innings")
    public List<ScorecardDto> scorecard(@PathVariable Long id) {
        return service.detail(id).scorecards();
    }

    @GetMapping("/{id}/commentary")
    @Operation(summary = "Ball-by-ball commentary, newest first, paginated")
    public PageDto<BallDto> commentary(@PathVariable Long id,
                                       @RequestParam(required = false) Integer innings,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "30") int size) {
        return service.commentary(id, innings, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a new match")
    public MatchDetailDto create(@Valid @RequestBody CreateMatchRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Conduct the toss and start a scheduled match")
    public MatchDetailDto start(@PathVariable Long id, @RequestBody(required = false) StartMatchRequest req) {
        return service.start(id, req);
    }

    @PostMapping("/{id}/deliveries")
    @Operation(summary = "Record one delivery (manual scoring)")
    public MatchDetailDto deliver(@PathVariable Long id, @Valid @RequestBody DeliveryRequest req) {
        return service.recordDelivery(id, req);
    }

    @PatchMapping("/{id}/simulation")
    @Operation(summary = "Pause/resume the automatic simulation of a match, or hand it to a manual scorer")
    public MatchDetailDto control(@PathVariable Long id, @RequestBody SimulationControlRequest req) {
        return service.control(id, req);
    }
}
