package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.MaintenanceFindingActionRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceFindingServiceImpl implements MaintenanceFindingService {

    private final ProjectRepository projectRepository;
    private final MaintenanceFindingRepository repository;
    private final MaintenanceFindingMapper mapper;

    @Override
    @Transactional
    public MaintenanceFindingResponse create(UUID projectId, CreateMaintenanceFindingRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        MaintenanceFinding finding = MaintenanceFinding.builder()
                .project(project)
                .contextSurface(request.contextSurface())
                .issueType(request.issueType())
                .severity(request.severity())
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(request.suggestedAction())
                .humanReviewRequired(request.humanReviewRequired())
                .summary(request.summary().trim())
                .details(normalizeDetails(request.details()))
                .build();

        return mapper.toResponse(repository.save(finding));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceFindingResponse> getByProject(UUID projectId) {
        ensureProjectExists(projectId);
        return mapper.toResponse(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse updateStatus(UUID projectId, UUID findingId, MaintenanceFindingStatus status) {
        ensureProjectExists(projectId);
        MaintenanceFinding finding = repository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));
        finding.setStatus(status);
        return mapper.toResponse(repository.save(finding));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse acknowledge(
            UUID projectId,
            UUID findingId,
            MaintenanceFindingActionRequest request
    ) {
        return applyAction(projectId, findingId, request, MaintenanceFindingActionType.ACKNOWLEDGE);
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse dismiss(
            UUID projectId,
            UUID findingId,
            MaintenanceFindingActionRequest request
    ) {
        return applyAction(projectId, findingId, request, MaintenanceFindingActionType.DISMISS);
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse resolve(
            UUID projectId,
            UUID findingId,
            MaintenanceFindingActionRequest request
    ) {
        return applyAction(projectId, findingId, request, MaintenanceFindingActionType.RESOLVE);
    }

    private void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
    }

    private String normalizeDetails(String details) {
        if (details == null) {
            return null;
        }
        String normalized = details.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private MaintenanceFindingResponse applyAction(
            UUID projectId,
            UUID findingId,
            MaintenanceFindingActionRequest request,
            MaintenanceFindingActionType actionType
    ) {
        ensureProjectExists(projectId);
        MaintenanceFinding finding = repository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (!supportsHumanRemediationWorkflow(finding)) {
            throw new ConflictException("This maintenance finding family does not yet support remediation actions.");
        }
        validateTransition(finding, actionType, request.comment());

        MaintenanceFindingAction action = MaintenanceFindingAction.builder()
                .finding(finding)
                .actionType(actionType)
                .actedBy(request.actedBy())
                .comment(normalizeDetails(request.comment()))
                .build();
        finding.getActions().add(0, action);
        finding.setStatus(targetStatus(actionType));

        return mapper.toResponse(repository.save(finding));
    }

    private boolean supportsHumanRemediationWorkflow(MaintenanceFinding finding) {
        return switch (finding.getIssueType()) {
            case STALE_HUMAN_CONTEXT_INPUT,
                    TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
                    TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE,
                    TRUSTED_KNOWLEDGE_OVERLAP_REVIEW -> true;
            default -> false;
        };
    }

    private void validateTransition(
            MaintenanceFinding finding,
            MaintenanceFindingActionType actionType,
            String comment
    ) {
        if (actionType == MaintenanceFindingActionType.ACKNOWLEDGE
                && finding.getStatus() != MaintenanceFindingStatus.OPEN) {
            throw new ConflictException("Only open maintenance findings can be acknowledged.");
        }
        if ((actionType == MaintenanceFindingActionType.DISMISS || actionType == MaintenanceFindingActionType.RESOLVE)
                && finding.getStatus() != MaintenanceFindingStatus.OPEN
                && finding.getStatus() != MaintenanceFindingStatus.ACKNOWLEDGED) {
            throw new ConflictException("Only open or acknowledged maintenance findings can be dismissed or resolved.");
        }
        if ((actionType == MaintenanceFindingActionType.DISMISS || actionType == MaintenanceFindingActionType.RESOLVE)
                && normalizeDetails(comment) == null) {
            throw new ConflictException("A rationale comment is required for this maintenance action.");
        }
    }

    private MaintenanceFindingStatus targetStatus(MaintenanceFindingActionType actionType) {
        return switch (actionType) {
            case ACKNOWLEDGE -> MaintenanceFindingStatus.ACKNOWLEDGED;
            case DISMISS -> MaintenanceFindingStatus.DISMISSED;
            case RESOLVE -> MaintenanceFindingStatus.RESOLVED;
        };
    }
}
