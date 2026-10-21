package com.cricpulse.repository;

import com.cricpulse.domain.Match;
import com.cricpulse.domain.MatchStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("select m.id from Match m where m.status = :status and m.autoSimulate = true and m.paused = false order by m.id")
    List<Long> findSimulatableIds(@Param("status") MatchStatus status);

    @Query("select m.id from Match m where m.status = 'SCHEDULED' and m.autoSimulate = true and m.startTime <= :now order by m.startTime")
    List<Long> findDueScheduledIds(@Param("now") Instant now);

    @Query("select m.id from Match m where m.status = :status order by m.startTime asc")
    List<Long> findIdsByStatusOldestFirst(@Param("status") MatchStatus status, Pageable page);

    @Query("select m.id from Match m where m.status = :status order by m.completedAt desc")
    List<Long> findIdsByStatusLatestCompleted(@Param("status") MatchStatus status, Pageable page);

    @Query("select m.id from Match m order by m.id")
    List<Long> findAllIds();

    long countByStatus(MatchStatus status);

    long countByStatusAndAutoSimulateTrue(MatchStatus status);

    @Query("select max(m.startTime) from Match m where m.status = 'SCHEDULED'")
    Instant findLatestScheduledStart();

    @Query("select m.teamA.id from Match m where m.status <> 'COMPLETED'")
    List<Long> findBusyTeamAIds();

    @Query("select m.teamB.id from Match m where m.status <> 'COMPLETED'")
    List<Long> findBusyTeamBIds();
}
