package com.hopeful117.devlogai.validation.repository;

import com.hopeful117.devlogai.validation.entity.Validation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ValidationRepository extends JpaRepository<Validation, UUID> {
    Optional<Validation> findByProposalId(UUID proposalId);

    boolean existsByProposalId(UUID proposalId);

    List<Validation> findByProposalIdIn(java.util.Collection<UUID> proposalIds);

    
}
