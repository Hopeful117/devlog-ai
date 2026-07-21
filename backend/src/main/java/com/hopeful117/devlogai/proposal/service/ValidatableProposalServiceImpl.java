package com.hopeful117.devlogai.proposal.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.dto.request.CreateValidatableProposalRequest;
import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.mapper.ValidatableProposalMapper;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ValidatableProposalServiceImpl implements ValidatableProposalService{
    private final ValidatableProposalRepository proposalRepository;
    private final ValidatableProposalMapper proposalMapper;

    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;

    @Override
    @Transactional
    public ValidatableProposalResponse create(
            CreateValidatableProposalRequest request
    ) {

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Project not found: " + request.projectId()
                        )
                );

        Analysis analysis = analysisRepository.findById(request.analysisId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Analysis not found: " + request.analysisId()
                        )
                );

        if (!analysis.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException(
                    "Analysis does not belong to the specified project"
            );
        }

        ValidatableProposal proposal =
                proposalMapper.toEntity(request);

        proposal.setProject(project);
        proposal.setAnalysis(analysis);

        ValidatableProposal saved =
                proposalRepository.save(proposal);

        return proposalMapper.toResponse(saved);
    }

    @Override
    public ValidatableProposalResponse getById(UUID id) {

        return proposalRepository.findById(id)
                .map(proposalMapper::toResponse)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Proposal not found: " + id
                        )
                );
    }


    @Override
    public List<ValidatableProposalResponse> getByProjectIdAndStatus(
            UUID projectId
    ) {

        return proposalRepository
                .findByProjectIdAndStatus(
                        projectId,
                        ProposalStatus.PROPOSED
                )
                .stream()
                .map(proposalMapper::toResponse)
                .toList();
    }

    @Override
    public List<ValidatableProposalResponse> getByProjectId(UUID projectId) {
        return proposalRepository.findByProjectId(projectId).stream().map(proposalMapper::toResponse).toList();
    }

    @Override
    public List<ValidatableProposalResponse> getByAnalysisId(
            UUID analysisId
    ) {

        return proposalRepository.findByAnalysisId(analysisId)
                .stream()
                .map(proposalMapper::toResponse)
                .toList();
    }
}
