package com.cricpulse.service;

import com.cricpulse.domain.Match;
import com.cricpulse.dto.MatchDtos.MatchDetailDto;
import com.cricpulse.repository.MatchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * The single entry point for changing a match. Updates to the same match are serialised by a
 * per-match lock (so the simulator and a human scorer can never interleave half-applied balls),
 * while different matches proceed in parallel. After the transaction commits, the new snapshot is
 * written to the cache and broadcast to subscribers.
 */
@Component
public class MatchUpdateExecutor {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final MatchRepository matches;
    private final SnapshotBuilder snapshots;
    private final LiveScoreCache cache;
    private final LiveUpdatePublisher publisher;

    public MatchUpdateExecutor(PlatformTransactionManager txManager, MatchRepository matches,
                               SnapshotBuilder snapshots, LiveScoreCache cache, LiveUpdatePublisher publisher) {
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.matches = matches;
        this.snapshots = snapshots;
        this.cache = cache;
        this.publisher = publisher;
    }

    /** Applies {@code work} to the match in one transaction, then caches and broadcasts the result. */
    public MatchDetailDto update(Long matchId, Consumer<Match> work) {
        ReentrantLock lock = lockFor(matchId);
        lock.lock();
        try {
            MatchDetailDto detail = tx.execute(status -> {
                Match match = load(matchId);
                work.accept(match);
                return snapshots.build(match);
            });
            cache.put(detail);
            publisher.publish(detail);
            return detail;
        } finally {
            lock.unlock();
        }
    }

    /** Rebuilds and broadcasts the snapshot of a match without changing it. */
    public MatchDetailDto refresh(Long matchId) {
        return update(matchId, m -> { });
    }

    /** Cached snapshot, built on first access. */
    public MatchDetailDto snapshot(Long matchId) {
        MatchDetailDto cached = cache.get(matchId).orElse(null);
        if (cached != null) {
            return cached;
        }
        ReentrantLock lock = lockFor(matchId);
        lock.lock();
        try {
            // Another thread may have built it while we waited.
            return cache.get(matchId).orElseGet(() -> {
                MatchDetailDto detail = readTx.execute(status -> snapshots.build(load(matchId)));
                cache.put(detail);
                return detail;
            });
        } finally {
            lock.unlock();
        }
    }

    private Match load(Long matchId) {
        return matches.findById(matchId).orElseThrow(() -> new NotFoundException("Match", matchId));
    }

    private ReentrantLock lockFor(Long matchId) {
        return locks.computeIfAbsent(matchId, id -> new ReentrantLock());
    }
}
