package com.hopeful117.devlogai.aicredentialbinding.repository;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface AiCredentialAssociationRepository extends JpaRepository<AiCredentialAssociation, UUID> {
    Optional<AiCredentialAssociation> findByPrincipalIdAndProvider(String principalId, String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select association from AiCredentialAssociation association "
            + "where association.principalId = :principalId and association.provider = :provider")
    Optional<AiCredentialAssociation> findByPrincipalIdAndProviderForUpdate(
            @Param("principalId") String principalId, @Param("provider") String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select association from AiCredentialAssociation association "
            + "where association.credentialHandle = :credentialHandle and association.provider = :provider")
    Optional<AiCredentialAssociation> findByCredentialHandleAndProviderForUpdate(
            @Param("credentialHandle") String credentialHandle, @Param("provider") String provider);
    Optional<AiCredentialAssociation> findByCredentialHandleAndProvider(String credentialHandle, String provider);
}
