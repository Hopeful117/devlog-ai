package com.hopeful117.devlogai.evaluation.liveauthorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface LiveEvaluationAuthorizationRepository extends JpaRepository<LiveEvaluationAuthorization, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authorization from LiveEvaluationAuthorization authorization "
            + "where authorization.idempotencyKeyHash = :keyHash "
            + "and authorization.preparation.id = :preparationId "
            + "and authorization.requesterId = :requesterId")
    Optional<LiveEvaluationAuthorization> findForUpdate(
            @Param("keyHash") String keyHash, @Param("preparationId") UUID preparationId,
            @Param("requesterId") String requesterId);

    @Query(value = "with lock_result as materialized (select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))) select 1 from lock_result", nativeQuery = true)
    Long acquireAuthorizationLock(@Param("lockKey") String lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authorization from LiveEvaluationAuthorization authorization "
            + "join fetch authorization.preparation preparation "
            + "where authorization.id = :id")
    Optional<LiveEvaluationAuthorization> findForUpdate(@Param("id") UUID id);
}
