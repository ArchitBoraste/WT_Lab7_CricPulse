package com.cricpulse.repository;

import com.cricpulse.domain.Innings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InningsRepository extends JpaRepository<Innings, Long> {
}
