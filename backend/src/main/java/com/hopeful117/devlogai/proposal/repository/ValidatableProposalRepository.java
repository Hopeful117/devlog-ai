package com.hopeful117.devlogai.proposal.repository;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
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

    @Query("select p from ValidatableProposal p where p.analysis.id = :analysisId order by " +
            "case when p.status = com.hopeful117.devlogai.proposal.entity.ProposalStatus.PROPOSED " +
            "then 0 else 1 end, case when p.sourceIndex is null then 1 else 0 end, " +
            "p.sourceIndex asc, p.createdAt asc, p.id asc")
    Page<ValidatableProposal> findReviewPage(
            @Param("analysisId") UUID analysisId, Pageable pageable);

    long countByAnalysisIdAndStatus(UUID analysisId, ProposalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ValidatableProposal p where p.id = :id")
    java.util.Optional<ValidatableProposal> findByIdForValidation(@Param("id") UUID id);

    boolean existsByAnalysisIdAndStatus(
            UUID analysisId,
            ProposalStatus status
    );

    long countByAiTaskId(UUID aiTaskId);
    long countByAnalysisId(UUID analysisId);
    List<ValidatableProposal> findByProjectId(UUID projectId);
}
