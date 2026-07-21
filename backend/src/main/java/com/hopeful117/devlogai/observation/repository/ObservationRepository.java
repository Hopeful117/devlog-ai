package com.hopeful117.devlogai.observation.repository;

import com.hopeful117.devlogai.observation.entity.Observation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ObservationRepository extends JpaRepository<Observation, UUID> {

    long countByAnalysisId(UUID analysisId);

    List<Observation> findByAnalysisIdOrderByCreatedAtDesc(UUID analysisId);

    List<Observation> findByAnalysisIdOrderByCreatedAtDescIdDesc(
            UUID analysisId,
            Pageable pageable
    );
}
