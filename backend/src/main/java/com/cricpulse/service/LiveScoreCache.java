package com.cricpulse.service;

import com.cricpulse.dto.MatchDtos.MatchDetailDto;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-through cache of the latest snapshot of every match. Each score update replaces the entry,
 * so reads (REST polling, page loads) are served from memory without touching the database,
 * no matter how many clients are watching.
 */
@Component
public class LiveScoreCache {

    private final ConcurrentHashMap<Long, MatchDetailDto> snapshots = new ConcurrentHashMap<>();

    public Optional<MatchDetailDto> get(Long matchId) {
        return Optional.ofNullable(snapshots.get(matchId));
    }

    public void put(MatchDetailDto detail) {
        snapshots.put(detail.summary().id(), detail);
    }

    public void evict(Long matchId) {
        snapshots.remove(matchId);
    }

    public int size() {
        return snapshots.size();
    }
}
