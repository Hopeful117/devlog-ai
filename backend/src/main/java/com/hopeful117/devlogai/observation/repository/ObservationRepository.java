package com.hopeful117.devlogai.observation.repository;

import com.hopeful117.devlogai.observation.entity.Observation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ObservationRepository extends JpaRepository<Observation, UUID> {

    long countByAnalysisId(UUID analysisId);

    List<Observation> findByAnalysisIdOrderByCreatedAtDesc(UUID analysisId);

    List<Observation> findByAnalysisIdOrderByTypeAscIdAsc(UUID analysisId);

    @EntityGraph(attributePaths = "supportingFacts")
    List<Observation> findByAnalysisIdOrderByCreatedAtDescIdDesc(
            UUID analysisId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "supportingFacts")
    List<Observation> findByAnalysisIdAndIdIn(UUID analysisId, java.util.Collection<UUID> ids);

    @Query("""
            select distinct observation
            from Observation observation
            join observation.supportingFacts supportingFact
            where observation.analysis.id in :analysisIds
              and supportingFact.id in :factIds
              and observation.analysis.id = supportingFact.analysis.id
            order by observation.type asc, observation.ruleId asc, observation.ruleVersion asc,
                     observation.content asc, observation.id asc
            """)
    List<Observation> findHistoricalCandidates(
            @Param("analysisIds") Collection<UUID> analysisIds,
            @Param("factIds") Collection<UUID> factIds,
            Pageable pageable
    );
}
