package com.cricpulse.repository;

import com.cricpulse.domain.BattingEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BattingEntryRepository extends JpaRepository<BattingEntry, Long> {

    @Query("""
            select new com.cricpulse.repository.BattingAggregate(
                p.id, p.name, t.shortName, t.color,
                count(b), sum(b.runs), sum(b.balls), max(b.runs), sum(b.fours), sum(b.sixes),
                sum(case when b.out = true then 1 else 0 end),
                sum(case when b.runs >= 50 and b.runs < 100 then 1 else 0 end),
                sum(case when b.runs >= 100 then 1 else 0 end))
            from BattingEntry b join b.player p join p.team t
            group by p.id, p.name, t.shortName, t.color
            """)
    List<BattingAggregate> aggregateAll();

    @EntityGraph(attributePaths = {"innings", "innings.match", "innings.bowlingTeam"})
    List<BattingEntry> findByPlayerIdOrderByIdDesc(Long playerId);
}
