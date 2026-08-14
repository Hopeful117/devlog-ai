package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MaintenanceEvaluationServiceImpl implements MaintenanceEvaluationService {

    private final ProjectRepository projectRepository;
    private final ProjectFreshnessService freshnessService;
    private final MaintenanceFindingRepository repository;
    private final MaintenanceFindingService findingService;

    public MaintenanceEvaluationServiceImpl(
            ProjectRepository projectRepository,
            ProjectFreshnessService freshnessService,
            MaintenanceFindingRepository repository,
            MaintenanceFindingService findingService
    ) {
        this.projectRepository = projectRepository;
        this.freshnessService = freshnessService;
        this.repository = repository;
        this.findingService = findingService;
    }

    @Override
    @Transactional
    public MaintenanceEvaluationResponse evaluate(UUID projectId) {
        ensureProjectExists(projectId);

        ProjectFreshnessSummary summary = freshnessService.summary(projectId);
        List<MaintenanceFindingResponse> created = new ArrayList<>();
        int skipped = 0;

        for (ProjectFreshnessResponse source : summary.checkedSources()) {
            if (source.status() != ProjectFreshnessStatus.STALE) {
                continue;
            }
            CreateMaintenanceFindingRequest request = staleUnderstandingRequest(source);
            if (hasEquivalentOpenFinding(projectId, request)) {
                skipped++;
                continue;
            }
            created.add(findingService.create(projectId, request));
        }

        if (summary.uncheckedSourceCount() > 0) {
            CreateMaintenanceFindingRequest request = missingFreshnessProjectionRequest(summary);
            if (hasEquivalentOpenFinding(projectId, request)) {
                skipped++;
            } else {
                created.add(findingService.create(projectId, request));
            }
        }

        return new MaintenanceEvaluationResponse(
                MaintenanceEvaluationResponse.PROJECTION_VERSION,
                projectId,
                created.size(),
                skipped,
                created
        );
    }

    private void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
    }

    private boolean hasEquivalentOpenFinding(UUID projectId, CreateMaintenanceFindingRequest request) {
        return repository.findByProject_IdAndStatusOrderByCreatedAtDescIdDesc(projectId, MaintenanceFindingStatus.OPEN)
                .stream()
                .anyMatch(existing ->
                        existing.getContextSurface() == request.contextSurface()
                                && existing.getIssueType() == request.issueType()
                                && existing.getSummary().equals(request.summary())
                                && sameDetails(existing.getDetails(), request.details()));
    }

    private boolean sameDetails(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private CreateMaintenanceFindingRequest staleUnderstandingRequest(ProjectFreshnessResponse source) {
        String summary = "Project understanding is stale for source '%s'."
                .formatted(source.source().name());
        String details = """
                Freshness check status is %s with guidance %s.
                Source requested revision: %s
                Source current revision: %s
                Baseline analyzed revision: %s
                Baseline completed at: %s
                Checked at: %s
                """.formatted(
                source.status(),
                source.guidance(),
                source.source().requestedRevision(),
                source.source().currentRevision(),
                source.baseline() == null ? "none" : source.baseline().analyzedRevision(),
                source.baseline() == null ? "none" : source.baseline().completedAt(),
                source.checkedAt()
        );
        return new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                MaintenanceFindingSeverity.HIGH,
                MaintenanceSuggestedActionCategory.REFRESH,
                false,
                summary,
                details
        );
    }

    private CreateMaintenanceFindingRequest missingFreshnessProjectionRequest(ProjectFreshnessSummary summary) {
        String summaryText = "Project freshness projection is missing for active sources.";
        String details = """
                Active sources without any persisted freshness check: %d
                Checked sources included in summary: %d
                Summary truncated: %s
                Run freshness checks before relying on freshness-based maintenance signals.
                """.formatted(
                summary.uncheckedSourceCount(),
                summary.checkedSources().size(),
                summary.truncated()
        );
        return new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_PROJECTION,
                MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH,
                MaintenanceFindingSeverity.MEDIUM,
                MaintenanceSuggestedActionCategory.REFRESH,
                false,
                summaryText,
                details
        );
    }
}
