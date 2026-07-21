package com.hopeful117.devlogai.validation.repository;

import com.hopeful117.devlogai.validation.entity.Validation;
import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ValidationRepository extends JpaRepository<Validation, UUID> {
    Optional<Validation> findByProposalId(UUID proposalId);

    boolean existsByProposalId(UUID proposalId);

    
}
