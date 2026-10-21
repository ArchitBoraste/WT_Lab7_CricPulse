package com.cricpulse.service;

import com.cricpulse.domain.Match;
import com.cricpulse.domain.MatchStatus;
import com.cricpulse.domain.Team;
import com.cricpulse.dto.MatchDtos.*;
import com.cricpulse.dto.PageDto;
import com.cricpulse.dto.Requests.*;
import com.cricpulse.repository.DeliveryRepository;
import com.cricpulse.repository.MatchRepository;
import com.cricpulse.repository.TeamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MatchService {

    private final MatchRepository matches;
    private final TeamRepository teams;
    private final DeliveryRepository deliveries;
    private final MatchUpdateExecutor executor;
    private final MatchLifecycle lifecycle;
    private final ScoringEngine scoring;

    public MatchService(MatchRepository matches, TeamRepository teams, DeliveryRepository deliveries,
                        MatchUpdateExecutor executor, MatchLifecycle lifecycle, ScoringEngine scoring) {
        this.matches = matches;
        this.teams = teams;
        this.deliveries = deliveries;
        this.executor = executor;
        this.lifecycle = lifecycle;
        this.scoring = scoring;
    }

    // ---------------------------------------------------------------- queries (served from cache)

    public DashboardDto dashboard(int limit) {
        return new DashboardDto(
                list(MatchStatus.LIVE, 50),
                list(MatchStatus.SCHEDULED, limit),
                list(MatchStatus.COMPLETED, limit));
    }

    public List<MatchSummaryDto> list(MatchStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        List<Long> ids;
        if (status == null) {
            ids = matches.findAllIds().stream().limit(page.getPageSize()).toList();
        } else if (status == MatchStatus.COMPLETED) {
            ids = matches.findIdsByStatusLatestCompleted(status, page);
        } else {
            ids = matches.findIdsByStatusOldestFirst(status, page);
        }
        return ids.stream().map(id -> executor.snapshot(id).summary()).toList();
    }

    public MatchDetailDto detail(Long id) {
        return executor.snapshot(id);
    }

    @Transactional(readOnly = true)
    public PageDto<BallDto> commentary(Long matchId, Integer innings, int page, int size) {
        if (!matches.existsById(matchId)) {
            throw new NotFoundException("Match", matchId);
        }
        PageRequest req = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 120)));
        Page<com.cricpulse.domain.Delivery> result = innings == null
                ? deliveries.findByMatchIdOrderByIdDesc(matchId, req)
                : deliveries.findByMatchIdAndInningsNumberOrderByIdDesc(matchId, innings, req);
        return PageDto.of(result, SnapshotBuilder::ball);
    }

    // ---------------------------------------------------------------- commands

    public MatchDetailDto create(CreateMatchRequest req) {
        if (req.teamAId().equals(req.teamBId())) {
            throw new ScoringException("A team cannot play against itself");
        }
        Team a = teams.findById(req.teamAId()).orElseThrow(() -> new NotFoundException("Team", req.teamAId()));
        Team b = teams.findById(req.teamBId()).orElseThrow(() -> new NotFoundException("Team", req.teamBId()));
        String venue = req.venue() == null || req.venue().isBlank() ? a.getHomeGround() : req.venue().trim();
        Instant start = req.startTime() == null ? Instant.now() : req.startTime();
        boolean auto = req.autoSimulate() == null || req.autoSimulate();
        Match saved = matches.save(new Match(a, b, venue, req.format(), start, auto));
        return executor.refresh(saved.getId());
    }

    public MatchDetailDto start(Long id, StartMatchRequest req) {
        return executor.update(id, m -> lifecycle.start(m,
                req == null ? null : req.tossWinnerId(), req == null ? null : req.decision()));
    }

    public MatchDetailDto recordDelivery(Long id, DeliveryRequest req) {
        return executor.update(id, m -> scoring.apply(m, req));
    }

    public MatchDetailDto control(Long id, SimulationControlRequest req) {
        return executor.update(id, m -> {
            if (req.paused() != null) m.setPaused(req.paused());
            if (req.autoSimulate() != null) m.setAutoSimulate(req.autoSimulate());
            m.touch();
        });
    }
}
