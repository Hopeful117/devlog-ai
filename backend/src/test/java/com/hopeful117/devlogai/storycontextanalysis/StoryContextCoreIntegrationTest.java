package com.hopeful117.devlogai.storycontextanalysis;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.dto.PromptExecutionMetadata;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineBindingClient;
import com.hopeful117.devlogai.aicredentialbinding.service.AiTaskExecutionAuthorizationService;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.authorization.ProjectMembership;
import com.hopeful117.devlogai.authorization.ProjectMembershipRepository;
import com.hopeful117.devlogai.authorization.ProjectRole;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.source.entity.GitProvider;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionRequest;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicy;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicyResolver;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real Core integration boundary for Q1 A/C. Only provider/transport-facing
 * services and repository synchronization are replaced with deterministic test
 * doubles; authorization, preparation, action execution, callback validation,
 * JPA persistence, and snapshots remain application beans.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"devlog.service-auth.secret=q1-test-secret", "sca.callback.secret=q1-test-secret",
                "devlog.security.oidc.issuer-uri="})
@Testcontainers
class StoryContextCoreIntegrationTest {
    private static final String PROJECT_SLUG = "q1-core-integration";
    private static final String PRINCIPAL_ID = "q1-integration-user";
    private static final String REVISION = "q1-integration-revision";
    private static final String PROVIDER = "mock";
    private static final String MODEL = "deterministic-v1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private AnalyzeStoryContextUseCase useCase;
    @Autowired private com.hopeful117.devlogai.storycontextanalysis.usecase.StoryContextInvestigationActionService actionService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMembershipRepository membershipRepository;
    @Autowired private EngineeringStoryRepository storyRepository;
    @Autowired private SourceRepository sourceRepository;
    @Autowired private AiTaskRepository aiTaskRepository;
    @Autowired private AnalysisRepository analysisRepository;
    @Autowired private ProjectProfileSnapshotRepository profileRepository;
    @Autowired private StoryContextAnalysisRepository storyContextAnalysisRepository;
    @Autowired private CapturingAiEngineClient aiEngineClient;
    @Autowired private WorkspaceManager workspaceManager;
    @Autowired private AiTaskExecutionAuthorizationService taskAuthorizationService;
    @Autowired private ObjectMapper objectMapper;
    @LocalServerPort private int localServerPort;

    @BeforeEach
    void resetCapture() {
        aiEngineClient.lastRequest = null;
        aiEngineClient.submitCount = 0;
    }

    @Test
    void oneShotTraversesRealCoreAuthorizationPreparationSubmissionCallbackAndPersistence(
            @TempDir Path workspace) throws Exception {
        Assumptions.assumeTrue(selected("Q1-A-DEVLOG-CURRENT-r1"));
        Fixture fixture = fixture(workspace, false);

        UUID taskId = useCase.execute(
                fixture.projectSlug, fixture.story.getId(), "engineering-story-context-analysis",
                List.of("docs/q1.md"), null, "q1-a-integration", fixture.principal,
                PROVIDER);

        assertThat(aiEngineClient.submitCount).isEqualTo(1);
        assertThat(aiEngineClient.lastRequest.aiTaskId()).isEqualTo(taskId);
        AiTask task = aiTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.getContextDigest()).isNotBlank();
        assertThat(task.getProjectionDigest()).isNotBlank();
        assertThat(task.getContextSnapshot()).containsKey("groundingContract");
        assertThat(task.getStatus().name()).isNotEqualTo("COMPLETED");

        if (Boolean.getBoolean("q1.core.fullPython")) {
            Map<String, Object> pythonArtifact = runPythonOneShot(task, workspace);
            AiTask completed = aiTaskRepository.findById(taskId).orElseThrow();
            assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");
            StoryContextAnalysis persisted = storyContextAnalysisRepository.findAll().stream()
                    .filter(value -> value.getAiTask().getId().equals(taskId))
                    .findFirst().orElseThrow();
            assertThat(persisted.getAnalysisSnapshot()).isNotEmpty();
            writeArtifact("Q1-A-DEVLOG-CURRENT-r1", completed,
                    completedCallback(completed), List.of(
                            "AnalyzeStoryContextUseCase", "StoryContextPreparationService",
                            "StoryContextSubmissionService", "StoryContextCallbackService",
                            "StoryContextCallbackIdentityValidator", "StoryContextResultValidator",
                            "AiTaskService", "StoryContextAnalysisRepository"), List.of(
                            "WorkspaceManager", "AiTaskExecutionAuthorizationService",
                            "AiEngineBindingClient", "StoryContextCompletionPolicyResolver"), pythonArtifact);
            return;
        }

        AiTaskResultRequest callback = completedCallback(task);
        useCase.handleCallback(task.getCorrelationId(), callback);

        AiTask completed = aiTaskRepository.findById(taskId).orElseThrow();
        assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");
        StoryContextAnalysis persisted = storyContextAnalysisRepository.findAll().stream()
                .filter(value -> value.getAiTask().getId().equals(taskId))
                .findFirst().orElseThrow();
        assertThat(persisted.getContextDigest()).isEqualTo(task.getContextDigest());
        assertThat(persisted.getAnalysisSnapshot()).isNotEmpty();
        assertThat(analysisRepository.findById(task.getAnalysis().getId())).isPresent();
        writeArtifact("Q1-A-DEVLOG-CURRENT-r1", task, callback, List.of(
                "AnalyzeStoryContextUseCase", "StoryContextPreparationService",
                "StoryContextSubmissionService", "StoryContextCallbackService",
                "StoryContextCallbackIdentityValidator", "StoryContextResultValidator",
                "AiTaskService", "StoryContextAnalysisRepository"), List.of(
                "AIEngineClient", "WorkspaceManager", "AiTaskExecutionAuthorizationService",
                "AiEngineBindingClient", "StoryContextCompletionPolicyResolver"));
    }

    @Test
    void boundedActionsTraverseRealCoreValidationExecutionAndSnapshotLedger(
            @TempDir Path workspace) throws Exception {
        Assumptions.assumeTrue(selected("Q1-C-DEVLOG-ENGINEERING-ANALYST-r1"));
        Fixture fixture = fixture(workspace, true);

        UUID taskId = useCase.execute(
                fixture.projectSlug, fixture.story.getId(), "engineering-story-context-analysis",
                List.of("docs/q1.md"), investigationGuidance(), "q1-c-integration",
                fixture.principal, PROVIDER);
        AiTask task = aiTaskRepository.findById(taskId).orElseThrow();
        Map<?, ?> investigation = (Map<?, ?>) task.getContextSnapshot().get("investigation");
        UUID executionId = UUID.fromString(String.valueOf(investigation.get("executionId")));

        if (Boolean.getBoolean("q1.core.fullPython")) {
            Map<String, Object> pythonArtifact = runPythonInvestigation(task, workspace);
            AiTask completed = aiTaskRepository.findById(taskId).orElseThrow();
            assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");
            StoryContextAnalysis persisted = storyContextAnalysisRepository.findAll().stream()
                    .filter(value -> value.getAiTask().getId().equals(taskId))
                    .findFirst().orElseThrow();
            assertThat(persisted.getAnalysisSnapshot()).isNotEmpty();
            writeArtifact("Q1-C-DEVLOG-ENGINEERING-ANALYST-r1", completed,
                    completedCallback(completed), List.of(
                            "AnalyzeStoryContextUseCase", "StoryContextPreparationService",
                            "StoryContextSubmissionService", "StoryContextInvestigationActionService",
                            "InvestigationCapabilityExecutionService", "StoryContextAgentProtocolController",
                            "StoryContextAgentCallbackFacade", "StoryContextCallbackService",
                            "StoryContextCallbackIdentityValidator", "StoryContextResultValidator",
                            "AiTaskService", "StoryContextAnalysisRepository"), List.of(
                            "AIEngineClient", "WorkspaceManager", "AiTaskExecutionAuthorizationService",
                            "AiEngineBindingClient", "StoryContextCompletionPolicyResolver"), pythonArtifact);
            return;
        }

        var search = actionService.execute(fixture.principal, taskId, action(
                fixture.projectSlug, "SEARCH_EVIDENCE", "search-1", executionId, task, "Core", null));
        assertThat(search.duplicate()).isFalse();
        assertThat(search.result()).containsKeys("outcome", "groundingContract", "groundingDigest");

        var read = actionService.execute(fixture.principal, taskId, action(
                fixture.projectSlug, "READ_EVIDENCE_RANGE", "read-1", executionId, task, null, "docs/q1.md"));
        assertThat(read.duplicate()).isFalse();
        assertThat(read.result()).containsEntry("kind", "READ_EVIDENCE_RANGE");

        AiTask updated = aiTaskRepository.findById(taskId).orElseThrow();
        Map<?, ?> updatedInvestigation = (Map<?, ?>) updated.getContextSnapshot().get("investigation");
        assertThat((List<?>) updatedInvestigation.get("actions")).hasSize(2);
        assertThat(((Map<?, ?>) updatedInvestigation.get("usage")).get("steps")).isEqualTo(2);

        AiTaskResultRequest callback = completedCallback(updated);
        useCase.handleCallback(updated.getCorrelationId(), callback);
        assertThat(aiTaskRepository.findById(taskId).orElseThrow().getStatus().name()).isEqualTo("COMPLETED");
        writeArtifact("Q1-C-DEVLOG-ENGINEERING-ANALYST-r1", updated, callback, List.of(
                "AnalyzeStoryContextUseCase", "StoryContextPreparationService",
                "StoryContextSubmissionService", "StoryContextInvestigationActionService",
                "InvestigationCapabilityExecutionService", "StoryContextCallbackService",
                "StoryContextCallbackIdentityValidator", "StoryContextResultValidator",
                "AiTaskService", "StoryContextAnalysisRepository"), List.of(
                "AIEngineClient", "WorkspaceManager", "AiTaskExecutionAuthorizationService",
                "AiEngineBindingClient", "StoryContextCompletionPolicyResolver"));
    }

    private boolean selected(String observationId) {
        String requested = System.getProperty("q1.core.observationId");
        return requested == null || requested.equals(observationId);
    }

    private void writeArtifact(String observationId, AiTask task, AiTaskResultRequest callback,
                               List<String> components, List<String> boundaryDoubles) throws Exception {
        writeArtifact(observationId, task, callback, components, boundaryDoubles, null);
    }

    private void writeArtifact(String observationId, AiTask task, AiTaskResultRequest callback,
                               List<String> components, List<String> boundaryDoubles,
                               Map<String, Object> pythonIntegration) throws Exception {
        String output = System.getProperty("q1.core.output");
        if (output == null || output.isBlank()) return;
        if (!Set.of("Q1-A-DEVLOG-CURRENT-r1", "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1").contains(observationId)) {
            throw new IllegalArgumentException("unsupported Core observation id");
        }
        Map<String, Object> snapshot = task.getContextSnapshot();
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("observationId", observationId);
        artifact.put("technicalStatus", "PASS");
        artifact.put("integrationStatus", "PASS");
        artifact.put("coreValidationStatus", "PASS");
        artifact.put("persistenceStatus", "PASS");
        artifact.put("componentsExecuted", components);
        artifact.put("simulatedBoundaries", boundaryDoubles);
        artifact.put("providerType", "SIMULATED_PROVIDER");
        artifact.put("answerStatus", "SIMULATED_CALLBACK_RESULT");
        artifact.put("externalProviderCalls", 0);
        artifact.put("externalNetworkCalls", 0);
        artifact.put("simulatedProviderCalls", 1);
        artifact.put("identities", Map.of(
                "provider", task.getProvider(),
                "modelIdentifier", task.getModelIdentifier(),
                "credentialBindingId", String.valueOf(task.getCredentialBindingId()),
                "taskId", String.valueOf(task.getId()),
                "correlationId", String.valueOf(task.getCorrelationId())));
        Map<String, Object> digests = new LinkedHashMap<>();
        digests.put("context", task.getContextDigest());
        digests.put("projection", task.getProjectionDigest());
        digests.put("grounding", snapshot.get("groundingDigest"));
        digests.put("submission", task.getSubmissionDigest());
        digests.put("callback", task.getTerminalCallbackDigest());
        artifact.put("digests", digests);
        artifact.put("investigationTrace", snapshot.getOrDefault("investigation", Map.of()));
        artifact.put("nativeResult", callback.analysisResult());
        if (pythonIntegration != null) artifact.put("pythonIntegration", pythonIntegration);
        Path outputPath = Path.of(output);
        Files.writeString(outputPath, objectMapper.writeValueAsString(artifact),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private Map<String, Object> runPythonInvestigation(AiTask task, Path workspace) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (!Files.isDirectory(root.resolve("ai-engine"))) root = root.getParent();
        if (root == null || !Files.isDirectory(root.resolve("ai-engine"))) {
            throw new IllegalStateException("DevLog AI repository root is unavailable");
        }
        Path prompt = workspace.resolve("q1-c-prompt.json");
        Path pythonArtifact = workspace.resolve("q1-c-python.json");
        objectMapper.writeValue(prompt.toFile(), aiEngineClient.lastRequest);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "uv", "run", "--project", root.resolve("ai-engine").toString(), "python", "-m",
                "evaluations.devlog_ai_abc_runner.q1_c_binding",
                "--prompt-file", prompt.toString(),
                "--core-url", "http://127.0.0.1:" + localServerPort,
                "--secret", "q1-test-secret",
                "--external-job-id", UUID.randomUUID().toString(),
                "--artifact", pythonArtifact.toString())
                .directory(root.toFile())
                .redirectErrorStream(true);
        String pythonPath = processBuilder.environment().get("PYTHONPATH");
        processBuilder.environment().put("PYTHONPATH", root.resolve("ai-engine")
                + (pythonPath == null || pythonPath.isBlank() ? "" : ":" + pythonPath));
        Process process = processBuilder.start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Python Q1 C binding timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Python Q1 C binding failed: " + output);
        }
        assertThat(pythonArtifact).exists();
        @SuppressWarnings("unchecked")
        Map<String, Object> artifact = objectMapper.readValue(
                Files.readString(pythonArtifact), Map.class);
        assertThat(artifact).containsEntry("status", "Q1_C_FULL_INVESTIGATION_INTEGRATION_PASS");
        assertThat(artifact).containsEntry("taskId", task.getId().toString());
        assertThat((List<?>) artifact.get("decisions")).hasSize(3);
        assertThat((List<?>) artifact.get("events")).hasSize(6);
        assertThat(artifact).containsEntry("externalProviderCalls", 0);
        assertThat(artifact).containsEntry("externalNetworkCalls", 0);
        String outputPath = System.getProperty("q1.core.output");
        if (outputPath != null && !outputPath.isBlank()) {
            Path destination = Path.of(outputPath).resolveSibling("python.json");
            Files.copy(pythonArtifact, destination);
        }
        return artifact;
    }

    private Map<String, Object> runPythonOneShot(AiTask task, Path workspace) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (!Files.isDirectory(root.resolve("ai-engine"))) root = root.getParent();
        if (root == null || !Files.isDirectory(root.resolve("ai-engine"))) {
            throw new IllegalStateException("DevLog AI repository root is unavailable");
        }
        Path prompt = workspace.resolve("q1-a-prompt.json");
        Path pythonArtifact = workspace.resolve("q1-a-python.json");
        objectMapper.writeValue(prompt.toFile(), aiEngineClient.lastRequest);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "uv", "run", "--project", root.resolve("ai-engine").toString(), "python", "-m",
                "evaluations.devlog_ai_abc_runner.q1_a_binding",
                "--prompt-file", prompt.toString(),
                "--core-url", "http://127.0.0.1:" + localServerPort,
                "--secret", "q1-test-secret",
                "--external-job-id", UUID.randomUUID().toString(),
                "--artifact", pythonArtifact.toString())
                .directory(root.toFile())
                .redirectErrorStream(true);
        String pythonPath = processBuilder.environment().get("PYTHONPATH");
        processBuilder.environment().put("PYTHONPATH", root.resolve("ai-engine")
                + (pythonPath == null || pythonPath.isBlank() ? "" : ":" + pythonPath));
        Process process = processBuilder.start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Python Q1 A binding timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Python Q1 A binding failed: " + output);
        }
        assertThat(pythonArtifact).exists();
        @SuppressWarnings("unchecked")
        Map<String, Object> artifact = objectMapper.readValue(
                Files.readString(pythonArtifact), Map.class);
        assertThat(artifact).containsEntry("status", "Q1_A_FULL_CALLBACK_INTEGRATION_PASS");
        assertThat(artifact).containsEntry("taskId", task.getId().toString());
        assertThat(artifact).containsEntry("providerCalls", 1);
        assertThat(artifact).containsEntry("externalProviderCalls", 0);
        assertThat(artifact).containsEntry("externalNetworkCalls", 0);
        String outputPath = System.getProperty("q1.core.output");
        if (outputPath != null && !outputPath.isBlank()) {
            Path destination = Path.of(outputPath).resolveSibling("python.json");
            Files.copy(pythonArtifact, destination);
        }
        return artifact;
    }

    private Fixture fixture(Path workspace, boolean investigation) throws Exception {
        Files.createDirectories(workspace.resolve("docs"));
        Files.writeString(workspace.resolve("docs/q1.md"), "Core authorizes the Story Context request.\n" +
                "The callback persists a non-trusted snapshot.\n");

        String projectSlug = PROJECT_SLUG + "-" + UUID.randomUUID();
        Project project = projectRepository.save(Project.builder().name(projectSlug + "-name")
                .slug(projectSlug).status(ProjectStatus.ACTIVE).build());
        EngineeringStory story = storyRepository.save(EngineeringStory.builder().project(project)
                .storyNumber(184).title("Bounded Story Context Investigation").status(StoryStatus.REGISTERED)
                .storyPath("docs/q1.md").build());
        Source source = sourceRepository.save(Source.builder().project(project).type(SourceType.GIT_REPOSITORY)
                .name("Q1 test source").repositoryUrl("file:///q1-test").defaultBranch("main")
                .provider(GitProvider.GENERIC_GIT).active(true).build());
        Analysis profileAnalysis = analysisRepository.save(Analysis.builder().project(project)
                .selectedSource(source).type(AnalysisType.STORY_CONTEXT_ANALYSIS)
                .intentId("project-understanding").intentVersion("v1")
                .status(AnalysisStatus.COMPLETED).targetRevision(REVISION)
                .userGuidance(Map.of()).createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build());
        profileRepository.save(ProjectProfileSnapshot.builder().id(UUID.randomUUID()).project(project)
                .analysis(profileAnalysis).profileVersion("test-profile-v1").rendererVersion("test-renderer-v1")
                .generatedAt(Instant.EPOCH).requestedRevision(REVISION)
                .resolvedRevisions(Map.of("source", REVISION)).completenessStatus(ProfileCompletenessStatus.COMPLETE)
                .collectionComplete(true).truncated(false).warningCount(0).errorCount(0)
                .successfulCollectorCount(1).collectorsWithWarningsCount(0).failedCollectorCount(0)
                .sections(List.of(Map.of("name", "test", "content", "Q1 integration profile")))
                .deterministicSummary("Q1 integration profile").sourceObservations(List.of())
                .characteristicCount(1).build());
        membershipRepository.save(ProjectMembership.builder().principalId(PRINCIPAL_ID)
                .project(project).role(ProjectRole.PROJECT_OWNER).build());
        doAnswer(invocation -> {
            Source resolvedSource = invocation.getArgument(0);
            return new SynchronizedWorkspace(resolvedSource == null ? null : resolvedSource.getId(), workspace, REVISION);
        }).when(workspaceManager).synchronize(any(), any());

        UUID bindingId = UUID.randomUUID();
        when(taskAuthorizationService.authorizeAndIssue(any(), any())).thenReturn(
                new AiTaskExecutionAuthorizationService.AuthorizedExecution(
                        AiCredentialBinding.builder().id(bindingId).build(), PROVIDER, MODEL,
                        Map.of("maxOutputTokens", 4096)));
        return new Fixture(projectSlug, story, delegatedPrincipal(project.getId()), investigation);
    }

    private AuthenticatedPrincipal delegatedPrincipal(UUID projectId) {
        return new AuthenticatedPrincipal(PRINCIPAL_ID, PrincipalKind.HUMAN, "integration-test",
                "q1-test-agent", "q1-test-grant", projectId.toString(), "MCP_STORY_AGENT_EXECUTE");
    }

    private Map<String, Object> investigationGuidance() {
        return Map.of("investigation", Map.of("enabled", true,
                "allowedPathPrefixes", List.of("docs"), "budget", Map.of(
                        "maximumSteps", 2, "maximumRetrievalCalls", 2, "maximumEvidenceItems", 10,
                        "maximumBytes", 20_000, "maximumTokens", 5_000,
                        "maximumElapsedMillis", 30_000, "maximumProviderOutputTokens", 1_000)));
    }

    private InvestigationActionRequest action(String projectSlug, String kind, String actionId, UUID executionId, AiTask task,
                                              String query, String path) {
        String requestedPath = "READ_EVIDENCE_RANGE".equals(kind) ? path : null;
        InvestigationActionRequest.Range range = "READ_EVIDENCE_RANGE".equals(kind)
                ? new InvestigationActionRequest.Range(1, 2) : null;
        return new InvestigationActionRequest(kind, actionId, projectSlug, REVISION, "Q1 Core evidence",
                new InvestigationActionRequest.Limits(2, 10_000, 2_000), query, requestedPath, range,
                "story-context-agent-protocol/v2", executionId, task.getId(), task.getCorrelationId(),
                PROVIDER, task.getCredentialBindingId(), 100, 1, 1L);
    }

    private AiTaskResultRequest completedCallback(AiTask task) {
        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) task.getContextSnapshot().get("scope");
        String projectSlug = String.valueOf(scope.get("projectSlug"));
        String storyId = String.valueOf(scope.get("storyId"));
        PromptExecutionMetadata metadata = new PromptExecutionMetadata(
                "story-context-analysis-prompt-v1", PROVIDER, MODEL, "a".repeat(64),
                task.getContextDigest(), null, task.getProjectionDigest(), "sca/v1",
                Map.of("projectSlug", projectSlug, "storyId", storyId,
                        "intent", "engineering-story-context-analysis", "files", List.of("docs/q1.md")),
                 (Map<String, Object>) task.getContextSnapshot().get("freshness"),
                 String.valueOf(task.getContextSnapshot().get("groundingDigest")));
        var result = new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult(
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.ObjectiveUnderstanding("Q1 Core flow"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.Confidence.LOW,
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.Provenance(
                        task.getContextDigest(), metadata.promptVersion(), PROVIDER, MODEL, "c".repeat(64),
                        "engineering-story-context-analysis", "v1", List.of(), Map.of()),
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.OutputClassification(List.of()));
        return new AiTaskResultRequest(task.getCorrelationId(), "q1-scripted-job", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, metadata, null, result);
    }

    private record Fixture(String projectSlug, EngineeringStory story, AuthenticatedPrincipal principal, boolean investigation) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class IntegrationDoubles {
        @Bean
        @Primary
        CapturingAiEngineClient aiEngineClient() { return new CapturingAiEngineClient(); }

        @Bean
        @Primary
        WorkspaceManager workspaceManager() { return mock(WorkspaceManager.class); }

        @Bean
        @Primary
        AiTaskExecutionAuthorizationService taskAuthorizationService() {
            return mock(AiTaskExecutionAuthorizationService.class);
        }

        @Bean
        @Primary
        StoryContextCompletionPolicyResolver completionPolicyResolver() {
            return (provider, model, intent, complexity) -> Optional.of(StoryContextCompletionPolicy.builder()
                    .policyVersion("story-context-completion-v1")
                    .provider(provider)
                    .modelIdentifier(model)
                    .intentId(intent)
                    .complexityClass(complexity)
                    .minimumOutputTokens(2_048)
                    .targetOutputTokens(6_000)
                    .maximumOutputTokens(16_384)
                    .contextCapacityTokens(1_048_576)
                    .measurementCertainty("TEST_FIXTURE")
                    .status("ACTIVE")
                    .createdAt(Instant.EPOCH)
                    .build());
        }

        @Bean
        @Primary
        AiEngineBindingClient aiEngineBindingClient() {
            AiEngineBindingClient client = mock(AiEngineBindingClient.class);
            doNothing().when(client).register(any());
            return client;
        }

        @Bean
        SecurityFilterChain q1HttpSecurity(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.ignoringRequestMatchers(
                            "/api/v1/story-context-agent/tasks/*/callback",
                            "/api/v1/story-context-agent/tasks/*/investigation/actions"))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/story-context-agent/tasks/*/callback",
                                    "/api/v1/story-context-agent/tasks/*/investigation/actions").permitAll()
                            .anyRequest().authenticated())
                    .build();
        }
    }

    static class CapturingAiEngineClient implements AIEngineClient {
        private PromptRequest lastRequest;
        private int submitCount;

        @Override
        public AiTaskSubmissionResponse submit(PromptRequest request) {
            lastRequest = request;
            submitCount++;
            return new AiTaskSubmissionResponse(request.correlationId(), true, "q1-scripted-job", Instant.now());
        }

        @Override
        public com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationResponse generateDeliverable(
                com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationRequest request) {
            throw new UnsupportedOperationException("not part of Q1 Core integration");
        }

        @Override
        public AiTaskSubmissionResponse submit(com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest request) {
            throw new UnsupportedOperationException("legacy transport is not part of Q1 Core integration");
        }
    }
}
