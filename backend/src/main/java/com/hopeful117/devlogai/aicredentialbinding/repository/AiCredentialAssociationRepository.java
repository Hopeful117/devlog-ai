package com.hopeful117.devlogai.aicredentialbinding.repository;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiCredentialAssociationRepository extends JpaRepository<AiCredentialAssociation, UUID> {
    Optional<AiCredentialAssociation> findByPrincipalIdAndProvider(String principalId, String provider);
}
