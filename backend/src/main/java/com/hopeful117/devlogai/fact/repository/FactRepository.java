package com.hopeful117.devlogai.fact.repository;

import com.hopeful117.devlogai.fact.entity.Fact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FactRepository extends JpaRepository<Fact, UUID> {

    List<Fact> findByAnalysisIdOrderByDetectedAtDesc(UUID analysisId);
}
