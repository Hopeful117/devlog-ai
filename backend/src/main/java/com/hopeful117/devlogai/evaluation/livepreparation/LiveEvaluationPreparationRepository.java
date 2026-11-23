package com.hopeful117.devlogai.evaluation.livepreparation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface LiveEvaluationPreparationRepository extends JpaRepository<LiveEvaluationPreparation, UUID> {
    Optional<LiveEvaluationPreparation> findByIdempotencyKeyHashAndIdempotencyScopeHash(
            String idempotencyKeyHash, String idempotencyScopeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preparation from LiveEvaluationPreparation preparation "
            + "where preparation.idempotencyKeyHash = :keyHash "
            + "and preparation.idempotencyScopeHash = :scopeHash")
    Optional<LiveEvaluationPreparation> findForUpdate(
            @Param("keyHash") String keyHash,
            @Param("scopeHash") String scopeHash);

    @Query(value = "with lock_result as materialized (select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) select 1 from lock_result", nativeQuery = true)
    Long acquirePreparationLock(@Param("lockKey") String lockKey);
}
