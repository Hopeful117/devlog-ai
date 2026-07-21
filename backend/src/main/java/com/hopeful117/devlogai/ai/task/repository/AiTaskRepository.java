package com.hopeful117.devlogai.ai.task.repository;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {

    Optional<AiTask> findByCorrelationId(UUID correlationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AiTask task where task.correlationId = :correlationId")
    Optional<AiTask> findByCorrelationIdForUpdate(
            @Param("correlationId") UUID correlationId
    );

    List<AiTask> findByAnalysisIdOrderByCreatedAtDescIdDesc(UUID analysisId);
}
