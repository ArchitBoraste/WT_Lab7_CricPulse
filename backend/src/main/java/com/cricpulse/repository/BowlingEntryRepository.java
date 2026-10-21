package com.cricpulse.repository;

import com.cricpulse.domain.BowlingEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BowlingEntryRepository extends JpaRepository<BowlingEntry, Long> {

    @Query("""
            select new com.cricpulse.repository.BowlingAggregate(
                p.id, p.name, t.shortName, t.color,
                count(b), sum(b.legalBalls), sum(b.runs), sum(b.wickets), sum(b.maidens), sum(b.dots),
                sum(case when b.wickets >= 3 then 1 else 0 end))
            from BowlingEntry b join b.player p join p.team t
            group by p.id, p.name, t.shortName, t.color
            """)
    List<BowlingAggregate> aggregateAll();

    @EntityGraph(attributePaths = {"innings", "innings.match", "innings.battingTeam"})
    List<BowlingEntry> findByPlayerIdOrderByIdDesc(Long playerId);
}
