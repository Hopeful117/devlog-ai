package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectunderstanding.ProjectUnderstandingService;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest;
import com.hopeful117.devlogai.projectcontextinput.service.ProjectHumanContextInputService;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceRemediationServiceImpl implements MaintenanceRemediationService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final MaintenanceFindingRepository findingRepository;
    private final MaintenanceFindingMapper findingMapper;
    private final ProjectFreshnessService freshnessService;
    private final SourceRepository sourceRepository;
    private final ProjectHumanContextInputService humanContextInputService;
    private final ProjectUnderstandingService understandingService;

    @Override
    @Transactional
    public MaintenanceFindingResponse refreshProjection(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP) {
            throw new ConflictException("This action is only available for PROJECTION_REFRESH_GAP findings.");
        }

        var sources = sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId);
        for (Source source : sources) {
            try {
                freshnessService.check(projectId, source.getId());
            } catch (Exception e) {
                log.warn("Freshness check failed for source {}: {}", source.getId(), e.getMessage());
            }
        }

        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);
        return findingMapper.toResponse(findingRepository.save(finding));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse archiveStaleHumanContext(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT) {
            throw new ConflictException("This action is only available for STALE_HUMAN_CONTEXT_INPUT findings.");
        }

        UUID inputId = extractInputIdFromDetails(finding.getDetails());
        if (inputId == null) {
            throw new ConflictException("Could not extract human context input ID from finding details.");
        }

        humanContextInputService.archive(projectId, inputId);
        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);
        return findingMapper.toResponse(findingRepository.save(finding));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse refreshMissingProjection(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH) {
            throw new ConflictException("This action is only available for MISSING_PROJECTION_REFRESH findings.");
        }

        var sources = sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId);
        for (Source source : sources) {
            try {
                freshnessService.check(projectId, source.getId());
            } catch (Exception e) {
                log.warn("Freshness check failed for source {}: {}", source.getId(), e.getMessage());
            }
        }

        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);
        return findingMapper.toResponse(findingRepository.save(finding));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse refreshProjectUnderstanding(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFinding finding = findingRepository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));

        if (finding.getIssueType() != MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING) {
            throw new ConflictException("This action is only available for STALE_PROJECT_UNDERSTANDING findings.");
        }

        var sources = sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId);
        for (Source source : sources) {
            try {
                freshnessService.check(projectId, source.getId());
            } catch (Exception e) {
                log.warn("Freshness check failed for source {}: {}", source.getId(), e.getMessage());
            }
        }

        try {
            understandingService.execute(projectId, new ProjectUnderstandingRequest(null, null, null));
        } catch (Exception e) {
            log.error("Understanding re-analysis failed: {}", e.getMessage());
            throw new ConflictException("Understanding re-analysis failed: " + e.getMessage());
        }

        applyAction(finding, MaintenanceFindingActionType.RESOLVE, actedBy, comment);
        return findingMapper.toResponse(findingRepository.save(finding));
    }

    private void applyAction(
            MaintenanceFinding finding,
            MaintenanceFindingActionType actionType,
            UUID actedBy,
            String comment
    ) {
        MaintenanceFindingAction action = MaintenanceFindingAction.builder()
                .finding(finding)
                .actionType(actionType)
                .actedBy(actedBy)
                .comment(normalizeDetails(comment))
                .build();
        finding.getActions().add(0, action);
        finding.setStatus(targetStatus(actionType));
    }

    private MaintenanceFindingStatus targetStatus(MaintenanceFindingActionType actionType) {
        return switch (actionType) {
            case ACKNOWLEDGE -> MaintenanceFindingStatus.ACKNOWLEDGED;
            case DISMISS -> MaintenanceFindingStatus.DISMISSED;
            case RESOLVE, AUTO_RESOLVE -> MaintenanceFindingStatus.RESOLVED;
        };
    }

    private String normalizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        return details.trim();
    }

    private UUID extractInputIdFromDetails(String details) {
        if (details == null) {
            return null;
        }
        Matcher matcher = UUID_PATTERN.matcher(details);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
