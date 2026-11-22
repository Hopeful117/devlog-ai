package com.hopeful117.devlogai.ai.task.repository;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.storycontextanalysis.service.SnapshotIdentity;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {

    Optional<AiTask> findByCorrelationId(UUID correlationId);

    Optional<AiTask> findBySubmissionDigest(String submissionDigest);

    Optional<AiTask> findByIdempotencyKeyHashAndIdempotencyScopeHash(
            String idempotencyKeyHash, String idempotencyScopeHash);

    Optional<AiTask> findByIdempotencyKeyHashAndIdempotencyScopeHashIsNull(String idempotencyKeyHash);

    Optional<AiTask> findByParentSnapshotId(UUID parentSnapshotId);

    /** Serializes the idempotency check and insert within the current transaction. */
    @Query(value = "with lock_result as materialized (select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) select 1 from lock_result", nativeQuery = true)
    Long acquireSubmissionLock(@Param("lockKey") String lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AiTask task where task.correlationId = :correlationId")
    Optional<AiTask> findByCorrelationIdForUpdate(
            @Param("correlationId") UUID correlationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AiTask task where task.id = :id")
    Optional<AiTask> findByIdForUpdate(@Param("id") UUID id);

    List<AiTask> findByAnalysisIdOrderByCreatedAtDescIdDesc(UUID analysisId);

    Optional<AiTask> findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(UUID analysisId);

    @Query("select task.id from AiTask task where task.status in :statuses and task.deadlineAt < :now order by task.deadlineAt asc, task.id asc")
    List<UUID> findIdsWithExpiredDeadline(
            @Param("statuses") List<AiTaskStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    List<AiTask> findByIntentIdAndIntentVersion(String intentId, String intentVersion);

    @Query("""
            select new com.hopeful117.devlogai.storycontextanalysis.service.SnapshotIdentity(
                task.id, analysis.project.id, task.taskType, task.createdAt, task.parentSnapshotId)
            from AiTask task join task.analysis analysis
            where task.id = :snapshotId and task.contextSnapshot is not null
            """)
    Optional<SnapshotIdentity> findStoryContextSnapshotIdentity(@Param("snapshotId") UUID snapshotId);
}
