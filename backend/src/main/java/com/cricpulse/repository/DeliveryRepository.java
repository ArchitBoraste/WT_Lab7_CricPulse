package com.cricpulse.repository;

import com.cricpulse.domain.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @EntityGraph(attributePaths = {"batter", "bowler", "dismissedPlayer"})
    List<Delivery> findByInningsIdOrderBySequenceAsc(Long inningsId);

    @EntityGraph(attributePaths = {"batter", "bowler", "dismissedPlayer", "innings"})
    Page<Delivery> findByMatchIdOrderByIdDesc(Long matchId, Pageable pageable);

    @EntityGraph(attributePaths = {"batter", "bowler", "dismissedPlayer", "innings"})
    Page<Delivery> findByMatchIdAndInningsNumberOrderByIdDesc(Long matchId, int inningsNumber, Pageable pageable);

    @Query("""
            select new com.cricpulse.repository.DeliveryTotals(
                count(d),
                coalesce(sum(d.runsOffBat + d.extraRuns), 0),
                coalesce(sum(case when d.runsOffBat = 4 then 1 else 0 end), 0),
                coalesce(sum(case when d.runsOffBat = 6 then 1 else 0 end), 0),
                coalesce(sum(case when d.wicket = true then 1 else 0 end), 0))
            from Delivery d
            """)
    DeliveryTotals totals();
}
