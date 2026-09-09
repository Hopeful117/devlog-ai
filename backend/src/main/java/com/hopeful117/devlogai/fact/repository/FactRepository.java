package com.hopeful117.devlogai.fact.repository;

import com.hopeful117.devlogai.fact.entity.Fact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface FactRepository extends JpaRepository<Fact, UUID> {

    long countByAnalysisId(UUID analysisId);

    List<Fact> findByAnalysisIdOrderByDetectedAtDesc(UUID analysisId);

    @EntityGraph(attributePaths = "evidenceReferences")
    List<Fact> findByAnalysisIdOrderByDetectedAtDescIdDesc(
            UUID analysisId,
            Pageable pageable
    );

    List<Fact> findByAnalysisIdAndIdIn(UUID analysisId, java.util.Collection<UUID> ids);

    @Query(value = """
            with fact_evidence as (
                select fer.fact_id,
                       string_agg(fer.reference, chr(31) order by fer.reference) as evidence_str
                from fact_evidence_references fer
                group by fer.fact_id
            )
            select fact.*
            from facts fact
            left join fact_evidence fe on fe.fact_id = fact.id
            where fact.analysis_id in :analysisIds
            order by fact.type asc, fact.source asc, fact.content asc,
                     coalesce(fe.evidence_str, '') asc,
                     fact.id asc
            """, nativeQuery = true)
    List<Fact> findHistoricalCandidates(
            @Param("analysisIds") java.util.Collection<UUID> analysisIds,
            Pageable pageable
    );

    @Query("select f.fingerprint from Fact f where f.analysis.id = :analysisId " +
            "and f.fingerprint is not null")
    Set<String> findFingerprintsByAnalysisId(@Param("analysisId") UUID analysisId);
}
