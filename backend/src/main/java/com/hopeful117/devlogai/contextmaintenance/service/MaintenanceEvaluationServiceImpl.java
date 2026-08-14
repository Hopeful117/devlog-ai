package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.agent.CrossSurfacePatternDetectionAgent;
import com.hopeful117.devlogai.contextmaintenance.agent.DuplicateAmbiguityResolutionAgent;
import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateAuditResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterCategory;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateMemberResponse;
import com.hopeful117.devlogai.insight.service.TrustedKnowledgeDuplicateAuditService;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.repository.ProjectHumanContextInputRepository;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Slf4j
public class MaintenanceEvaluationServiceImpl implements MaintenanceEvaluationService {

    static final UUID SYSTEM_AUTOMATION_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ProjectRepository projectRepository;
    private final ProjectFreshnessService freshnessService;
    private final TrustedKnowledgeDuplicateAuditService duplicateAuditService;
    private final ProjectHumanContextInputRepository humanContextInputRepository;
    private final MaintenanceFindingRepository repository;
    private final MaintenanceFindingService findingService;
    private final MaintenanceAssessmentService assessmentService;
    private final DuplicateAmbiguityResolutionAgent duplicateAgent;
    private final CrossSurfacePatternDetectionAgent crossSurfaceAgent;

    public MaintenanceEvaluationServiceImpl(
            ProjectRepository projectRepository,
            ProjectFreshnessService freshnessService,
            TrustedKnowledgeDuplicateAuditService duplicateAuditService,
            ProjectHumanContextInputRepository humanContextInputRepository,
            MaintenanceFindingRepository repository,
            MaintenanceFindingService findingService,
            MaintenanceAssessmentService assessmentService,
            DuplicateAmbiguityResolutionAgent duplicateAgent,
            CrossSurfacePatternDetectionAgent crossSurfaceAgent
    ) {
        this.projectRepository = projectRepository;
        this.freshnessService = freshnessService;
        this.duplicateAuditService = duplicateAuditService;
        this.humanContextInputRepository = humanContextInputRepository;
        this.repository = repository;
        this.findingService = findingService;
        this.assessmentService = assessmentService;
        this.duplicateAgent = duplicateAgent;
        this.crossSurfaceAgent = crossSurfaceAgent;
    }

    @Override
    @Transactional
    public MaintenanceEvaluationResponse evaluate(UUID projectId) {
        ensureProjectExists(projectId);

        ProjectFreshnessSummary summary = freshnessService.summary(projectId);
        InsightDuplicateAuditResponse duplicateAudit = duplicateAuditService.audit(projectId);
        List<MaintenanceFinding> currentFindings = repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId);
        List<MaintenanceFindingResponse> created = new ArrayList<>();
        int skipped = 0;
        Set<String> activeDeterministicKeys = new HashSet<>();

        for (ProjectFreshnessResponse source : summary.checkedSources()) {
            if (source.status() != ProjectFreshnessStatus.STALE) {
                continue;
            }
            CreateMaintenanceFindingRequest request = staleUnderstandingRequest(source);
            activeDeterministicKeys.add(keyFor(request));
            if (hasEquivalentActiveFinding(currentFindings, request)) {
                skipped++;
                continue;
            }
            created.add(findingService.create(projectId, request));
        }

        if (summary.uncheckedSourceCount() > 0) {
            CreateMaintenanceFindingRequest request = missingFreshnessProjectionRequest(summary);
            activeDeterministicKeys.add(keyFor(request));
            if (hasEquivalentActiveFinding(currentFindings, request)) {
                skipped++;
            } else {
                created.add(findingService.create(projectId, request));
            }
        }

        for (ProjectHumanContextInput input : staleHumanContextCandidates(projectId)) {
            CreateMaintenanceFindingRequest request = staleHumanContextRequest(input);
            activeDeterministicKeys.add(keyFor(request));
            if (hasEquivalentActiveFinding(currentFindings, request)) {
                skipped++;
                continue;
            }
            created.add(findingService.create(projectId, request));
        }

        for (InsightDuplicateClusterResponse cluster : duplicateAudit.clusters()) {
            CreateMaintenanceFindingRequest request = duplicateDebtRequest(cluster);
            if (request == null) {
                continue;
            }
            if (hasEquivalentActiveFinding(currentFindings, request)) {
                skipped++;
                continue;
            }
            MaintenanceFindingResponse createdFinding = findingService.create(projectId, request);
            created.add(createdFinding);
            evaluateDuplicateFinding(projectId, createdFinding, cluster);
        }

        autoResolveClearedDeterministicFindings(projectId, currentFindings, activeDeterministicKeys);

        evaluateCrossSurfacePatterns(projectId, currentFindings, created);

        return new MaintenanceEvaluationResponse(
                MaintenanceEvaluationResponse.PROJECTION_VERSION,
                projectId,
                created.size(),
                skipped,
                created
        );
    }

    private List<ProjectHumanContextInput> staleHumanContextCandidates(UUID projectId) {
        List<ProjectHumanContextInput> activeInputs = humanContextInputRepository
                .findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(projectId, ProjectHumanContextInputStatus.ACTIVE);
        if (activeInputs.size() < 2) {
            return List.of();
        }

        Map<com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType, ProjectHumanContextInput>
                newestByType = new HashMap<>();
        List<ProjectHumanContextInput> staleCandidates = new ArrayList<>();
        for (ProjectHumanContextInput input : activeInputs) {
            ProjectHumanContextInput newest = newestByType.get(input.getType());
            if (newest == null) {
                newestByType.put(input.getType(), input);
                continue;
            }

            if (input.getUpdatedAt() == null || newest.getUpdatedAt() == null) {
                continue;
            }

            long ageDays = java.time.Duration.between(input.getUpdatedAt(), java.time.Instant.now()).toDays();
            long lagDays = java.time.Duration.between(input.getUpdatedAt(), newest.getUpdatedAt()).toDays();
            if (ageDays >= 30 && lagDays >= 14) {
                staleCandidates.add(input);
            }
        }
        return staleCandidates;
    }

    private CreateMaintenanceFindingRequest staleHumanContextRequest(ProjectHumanContextInput input) {
        String summary = "Active human context input '%s' may be stale or superseded."
                .formatted(input.getTitle());
        String details = """
                Input type: %s
                Current status: %s
                Last updated at: %s
                This active note is older than a newer active note of the same type.
                Review whether it should remain active, be archived, or be replaced by fresher context.
                """.formatted(
                input.getType(),
                input.getStatus(),
                input.getUpdatedAt()
        );
        return new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.INTERNAL_HUMAN_CONTEXT,
                MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT,
                MaintenanceFindingSeverity.MEDIUM,
                MaintenanceSuggestedActionCategory.REVIEW,
                true,
                summary,
                details
        );
    }

    private CreateMaintenanceFindingRequest duplicateDebtRequest(InsightDuplicateClusterResponse cluster) {
        return switch (cluster.category()) {
            case EXACT_DUPLICATE -> new CreateMaintenanceFindingRequest(
                    MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                    MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
                    MaintenanceFindingSeverity.HIGH,
                    MaintenanceSuggestedActionCategory.REVIEW,
                    true,
                    "Trusted knowledge exact duplicate debt detected for cluster '%s'."
                            .formatted(cluster.clusterKey()),
                    duplicateClusterDetails(cluster)
            );
            case LIKELY_SEMANTIC_DUPLICATE -> new CreateMaintenanceFindingRequest(
                    MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                    MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE,
                    MaintenanceFindingSeverity.MEDIUM,
                    MaintenanceSuggestedActionCategory.REVIEW,
                    true,
                    "Trusted knowledge semantic duplicate candidate detected for cluster '%s'."
                            .formatted(cluster.clusterKey()),
                    duplicateClusterDetails(cluster)
            );
            case LIKELY_RICHER_SUCCESSOR, REVIEW_REQUIRED -> new CreateMaintenanceFindingRequest(
                    MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                    MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW,
                    MaintenanceFindingSeverity.MEDIUM,
                    MaintenanceSuggestedActionCategory.REVIEW,
                    true,
                    "Trusted knowledge overlap requires review for cluster '%s'."
                            .formatted(cluster.clusterKey()),
                    duplicateClusterDetails(cluster)
            );
        };
    }

    private void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
    }

    private boolean hasEquivalentActiveFinding(List<MaintenanceFinding> findings, CreateMaintenanceFindingRequest request) {
        return findings.stream()
                .filter(existing -> existing.getStatus() == MaintenanceFindingStatus.OPEN
                        || existing.getStatus() == MaintenanceFindingStatus.ACKNOWLEDGED)
                .anyMatch(existing ->
                        existing.getContextSurface() == request.contextSurface()
                                && existing.getIssueType() == request.issueType()
                                && sameText(existing.getSummary(), request.summary())
                                && sameText(existing.getDetails(), request.details()));
    }

    private void autoResolveClearedDeterministicFindings(
            UUID projectId,
            List<MaintenanceFinding> currentFindings,
            Set<String> activeDeterministicKeys
    ) {
        currentFindings.stream()
                .filter(this::supportsAutomaticResolution)
                .filter(finding -> finding.getStatus() == MaintenanceFindingStatus.OPEN
                        || finding.getStatus() == MaintenanceFindingStatus.ACKNOWLEDGED)
                .filter(finding -> !activeDeterministicKeys.contains(keyFor(finding)))
                .forEach(finding -> findingService.autoResolve(
                        projectId,
                        finding.getId(),
                        SYSTEM_AUTOMATION_ACTOR_ID,
                        "Automatically resolved because the deterministic maintenance condition no longer applies."
                ));
    }

    private boolean supportsAutomaticResolution(MaintenanceFinding finding) {
        return switch (finding.getIssueType()) {
            case STALE_PROJECT_UNDERSTANDING, MISSING_PROJECTION_REFRESH, STALE_HUMAN_CONTEXT_INPUT -> true;
            default -> false;
        };
    }

    private String keyFor(CreateMaintenanceFindingRequest request) {
        return "%s|%s|%s|%s".formatted(
                request.contextSurface(),
                request.issueType(),
                normalizeText(request.summary()),
                normalizeText(request.details())
        );
    }

    private String keyFor(MaintenanceFinding finding) {
        return "%s|%s|%s|%s".formatted(
                finding.getContextSurface(),
                finding.getIssueType(),
                normalizeText(finding.getSummary()),
                normalizeText(finding.getDetails())
        );
    }

    private boolean sameText(String left, String right) {
        return normalizeText(left).equals(normalizeText(right));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
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

    private String duplicateClusterDetails(InsightDuplicateClusterResponse cluster) {
        String members = cluster.members().stream()
                .sorted(Comparator.comparing(InsightDuplicateMemberResponse::createdAt).reversed()
                        .thenComparing(InsightDuplicateMemberResponse::insightId, Comparator.reverseOrder()))
                .map(member -> "%s | %s".formatted(member.insightId(), member.title()))
                .collect(Collectors.joining("\n"));
        return """
                Duplicate cluster key: %s
                Cluster category: %s
                Recommendation: %s
                Member count: %d
                Detector rationale: %s
                Members:
                %s
                """.formatted(
                cluster.clusterKey(),
                cluster.category(),
                cluster.recommendation(),
                cluster.members().size(),
                cluster.rationale(),
                members
        );
    }

    private void evaluateDuplicateFinding(
            UUID projectId,
            MaintenanceFindingResponse finding,
            InsightDuplicateClusterResponse cluster
    ) {
        try {
            duplicateAgent.evaluate(finding.issueType().name(), cluster)
                    .ifPresent(assessment -> {
                        CreateMaintenanceAssessmentRequest request = new CreateMaintenanceAssessmentRequest(
                                finding.id(),
                                assessment.confidenceLevel(),
                                assessment.semanticClassification(),
                                assessment.recommendedAction(),
                                assessment.rationale(),
                                assessment.supportingSignals()
                        );
                        assessmentService.create(projectId, request);
                        log.info("Agent assessment created for findingId={} classification={}",
                                finding.id(), assessment.semanticClassification());
                    });
        } catch (Exception e) {
            log.warn("Failed to evaluate duplicate finding {} with agent: {}",
                    finding.id(), e.getMessage());
        }
    }

    private void evaluateCrossSurfacePatterns(
            UUID projectId,
            List<MaintenanceFinding> currentFindings,
            List<MaintenanceFindingResponse> created
    ) {
        try {
            List<MaintenanceFinding> allActiveFindings = new ArrayList<>(currentFindings);
            created.stream()
                    .map(response -> repository.findByIdAndProject_Id(response.id(), projectId).orElse(null))
                    .filter(Objects::nonNull)
                    .forEach(allActiveFindings::add);

            crossSurfaceAgent.evaluate(allActiveFindings)
                    .ifPresent(assessment -> {
                        UUID representativeFindingId = assessment.contributingFindingIds().getFirst();
                        CreateMaintenanceAssessmentRequest request = new CreateMaintenanceAssessmentRequest(
                                representativeFindingId,
                                assessment.confidenceLevel(),
                                assessment.semanticClassification(),
                                assessment.recommendedAction(),
                                assessment.rationale(),
                                assessment.supportingSignals()
                        );
                        assessmentService.create(projectId, request);
                        log.info("Cross-surface pattern assessment created for project={} classification={} findingCount={}",
                                projectId, assessment.semanticClassification(),
                                assessment.contributingFindingIds().size());
                    });
        } catch (Exception e) {
            log.warn("Failed to evaluate cross-surface patterns for project {}: {}",
                    projectId, e.getMessage());
        }
    }
}
