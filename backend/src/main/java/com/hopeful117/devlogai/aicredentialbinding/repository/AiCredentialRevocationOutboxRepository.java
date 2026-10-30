package com.hopeful117.devlogai.aicredentialbinding.repository;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialRevocationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface AiCredentialRevocationOutboxRepository extends JpaRepository<AiCredentialRevocationOutbox, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    AiCredentialRevocationOutbox findTop1ByOrderByCreatedAtAsc();

    @Modifying
    @Transactional
    @Query(value = """
            insert into ai_credential_revocation_outbox
                (id, credential_handle, provider, created_at, attempts)
            values (:id, :credentialHandle, :provider, :createdAt, 0)
            on conflict (credential_handle, provider) do nothing
            """, nativeQuery = true)
    int enqueueIfAbsent(@Param("id") UUID id,
                        @Param("credentialHandle") String credentialHandle,
                        @Param("provider") String provider,
                        @Param("createdAt") Instant createdAt);
}
