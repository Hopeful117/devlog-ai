package com.hopeful117.devlogai.aicredentialbinding.repository;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface AiCredentialBindingRepository extends JpaRepository<AiCredentialBinding, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from AiCredentialBinding binding "
            + "join fetch binding.aiTask task join fetch task.analysis analysis "
            + "join fetch analysis.project project where binding.id = :id")
    Optional<AiCredentialBinding> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByAiTaskIdAndProviderAndStatus(
            UUID aiTaskId, String provider,
            com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBindingStatus status);
}
