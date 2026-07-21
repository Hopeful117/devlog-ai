package com.hopeful117.devlogai.ai.task.repository;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {

    Optional<AiTask> findByCorrelationId(UUID correlationId);

    List<AiTask> findByAnalysisIdOrderByCreatedAtDescIdDesc(UUID analysisId);
}
