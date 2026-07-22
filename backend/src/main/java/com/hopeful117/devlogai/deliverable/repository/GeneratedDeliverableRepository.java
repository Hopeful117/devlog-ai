package com.hopeful117.devlogai.deliverable.repository;

import com.hopeful117.devlogai.deliverable.entity.GeneratedDeliverable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDeliverableRepository extends JpaRepository<GeneratedDeliverable, UUID> {
    @Override
    @EntityGraph(attributePaths = {"project", "analysis", "sourceInsights"})
    Optional<GeneratedDeliverable> findById(UUID id);

    @EntityGraph(attributePaths = {"project", "analysis", "sourceInsights"})
    List<GeneratedDeliverable> findByProjectIdOrderByGeneratedAtDesc(UUID projectId);

    @EntityGraph(attributePaths = {"project", "analysis", "sourceInsights"})
    List<GeneratedDeliverable> findByAnalysisIdOrderByGeneratedAtDesc(UUID analysisId);
}
