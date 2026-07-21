package com.hopeful117.devlogai.observation.repository;

import com.hopeful117.devlogai.observation.entity.Observation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ObservationRepository extends JpaRepository<Observation, UUID> {

    List<Observation> findByAnalysisIdOrderByCreatedAtDesc(UUID analysisId);
}
