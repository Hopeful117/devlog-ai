package com.hopeful117.devlogai.fact.repository;

import com.hopeful117.devlogai.fact.entity.Fact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface FactRepository extends JpaRepository<Fact, UUID> {

    long countByAnalysisId(UUID analysisId);

    List<Fact> findByAnalysisIdOrderByDetectedAtDesc(UUID analysisId);

    List<Fact> findByAnalysisIdOrderByDetectedAtDescIdDesc(
            UUID analysisId,
            Pageable pageable
    );

    @Query("select f.fingerprint from Fact f where f.analysis.id = :analysisId " +
            "and f.fingerprint is not null")
    Set<String> findFingerprintsByAnalysisId(@Param("analysisId") UUID analysisId);
}
