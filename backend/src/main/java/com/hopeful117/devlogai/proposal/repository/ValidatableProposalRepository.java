package com.hopeful117.devlogai.proposal.repository;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidatableProposalRepository extends JpaRepository<ValidatableProposal, UUID> {

    List<ValidatableProposal> findByProjectIdAndStatus(
            UUID projectId,
            ProposalStatus status
    );

    List<ValidatableProposal> findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID projectId,
            ProposalStatus status,
            Pageable pageable
    );

    List<ValidatableProposal> findByAnalysisId(
            UUID analysisId
    );

    boolean existsByAnalysisIdAndStatus(
            UUID analysisId,
            ProposalStatus status
    );

    long countByAiTaskId(UUID aiTaskId);
    long countByAnalysisId(UUID analysisId);
    List<ValidatableProposal> findByProjectId(UUID projectId);
}
