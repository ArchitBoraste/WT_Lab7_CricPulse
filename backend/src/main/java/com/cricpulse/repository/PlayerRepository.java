package com.cricpulse.repository;

import com.cricpulse.domain.Player;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeamIdOrderByBattingOrderAsc(Long teamId);

    List<Player> findByTeamIdOrderByBowlingSkillDesc(Long teamId);

    @EntityGraph(attributePaths = "team")
    @Query("""
            select p from Player p
            where (:teamId is null or p.team.id = :teamId)
              and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))
            order by p.team.id, p.battingOrder
            """)
    List<Player> search(@Param("teamId") Long teamId, @Param("q") String q);
}
