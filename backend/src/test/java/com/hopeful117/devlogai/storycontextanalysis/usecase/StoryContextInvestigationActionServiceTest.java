package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionKind;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionRequest;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationCapabilityExecutionService;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationCapabilityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryContextInvestigationActionServiceTest {
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID STORY_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID BINDING_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();
    private static final String REVISION = "revision-1";

    @Mock AiTaskRepository repository;
    @Mock CoreProjectAuthorizationService authorization;
    @Mock InvestigationCapabilityExecutionService executor;

    @Test
    void rejectsTaskWithoutExplicitInvestigationInitialization() {
        AiTask task = task(Map.of("scope", scope(), "freshness", freshness(), "investigation", Map.of()));
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);

        assertThatThrownBy(() -> service().execute(principal(), TASK_ID, request()))
                .isInstanceOf(com.hopeful117.devlogai.shared.exception.ConflictException.class)
                .hasMessageContaining("explicitly initialized");
        verifyNoInteractions(executor);
    }

    @Test
    void executesOnceAndReturnsStoredMetadataForDuplicateAction() {
        AiTask task = task(Map.of(
                "scope", scope(), "freshness", freshness(),
                "investigation", Map.of("initialized", true, "scope", Map.of(
                        "projectSlug", "devlog", "revision", REVISION, "allowedPathPrefixes", List.of("backend")),
                        "budget", budget(), "usage", usage())));
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);
        when(executor.execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any()))
                .thenReturn(new InvestigationCapabilityResult(PROJECT_ID, STORY_ID, "action-1",
                        InvestigationActionKind.SEARCH_EVIDENCE, "devlog", REVISION,
                        InvestigationCapabilityResult.Outcome.EMPTY, List.of(), null, null));

        var first = service().execute(principal(), TASK_ID, request());
        var second = service().execute(principal(), TASK_ID, request());

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.result()).isEqualTo(first.result());
        assertThat(task.getContextSnapshot()).containsKey("investigation");
        Map<String, Object> investigation = (Map<String, Object>) task.getContextSnapshot().get("investigation");
        Map<String, Object> usage = (Map<String, Object>) investigation.get("usage");
        assertThat(investigation).containsEntry("accountedProviderGenerationCount", 1);
        assertThat(usage).containsEntry("providerOutputTokens", 0);
        assertThat(((Number) usage.get("elapsedMillis")).longValue()).isGreaterThanOrEqualTo(7L);
        verify(executor, times(1)).execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateActionIdBoundToDifferentRequest() {
        AiTask task = task(snapshotWithGrounding());
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);
        when(executor.execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any()))
                .thenReturn(result(false, "retrieved:backend/App.java@revision-1"));

        service().execute(principal(), TASK_ID, requestWithQuery("action-1", "find source", 0));

        assertThatThrownBy(() -> service().execute(principal(), TASK_ID,
                requestWithQuery("action-1", "find a different source", 0)))
                .isInstanceOf(com.hopeful117.devlogai.shared.exception.ConflictException.class)
                .hasMessageContaining("different action request");
        verify(executor, times(1)).execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any());
    }

    @Test
    void accountsRetrievalUsageAndNonCumulativeProviderOutput() {
        AiTask task = task(snapshotWithGrounding());
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);
        when(executor.execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any()))
                .thenReturn(result(false, "retrieved:backend/App.java@revision-1"));

        service().execute(principal(), TASK_ID, requestWith("action-1", 100));
        service().execute(principal(), TASK_ID, requestWith("action-2", 50));

        Map<String, Object> investigation = (Map<String, Object>) task.getContextSnapshot().get("investigation");
        Map<String, Object> usage = (Map<String, Object>) investigation.get("usage");
        assertThat(usage).containsEntry("steps", 2).containsEntry("retrievalCalls", 2)
                .containsEntry("evidenceItems", 2).containsEntry("providerOutputTokens", 150);
        assertThat(investigation).containsEntry("accountedProviderGenerationCount", 2);
        assertThat(((Number) usage.get("bytes")).longValue()).isGreaterThan(0);
        assertThat(((Number) usage.get("tokens")).longValue()).isGreaterThan(0);
        assertThat(((Number) usage.get("elapsedMillis")).longValue()).isGreaterThanOrEqualTo(14);
    }

    @Test
    void appendsCitableRetrievedEvidenceToFinalGroundingAndReferenceMapping() {
        AiTask task = task(snapshotWithGrounding());
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);
        when(executor.execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any()))
                .thenReturn(result(true, "retrieved:backend/App.java@revision-1"));

        var response = service().execute(principal(), TASK_ID, request());

        Map<String, Object> snapshot = task.getContextSnapshot();
        Map<String, Object> contract = (Map<String, Object>) snapshot.get("groundingContract");
        Map<String, Object> entry = (Map<String, Object>) ((List<?>) contract.get("allowedGroundingReferences")).getFirst();
        assertThat(entry).containsEntry("ref", "retrieved:backend/App.java@revision-1")
                .containsEntry("citable", true).containsEntry("revision", REVISION)
                .containsEntry("project", "devlog");
        assertThat(response.result()).containsKeys("protocolVersion", "executionId", "parentTaskId",
                "actionId", "kind", "projectSlug", "revision", "outcome", "evidence",
                "groundingContract", "groundingDigest");
        assertThat(response.result().get("groundingDigest"))
                .isEqualTo(snapshot.get("groundingDigest"));
        var allowed = AnalyzeStoryContextUseCase.allowedGroundingReferences(contract, task);
        assertThat(allowed).contains("retrieved:backend/App.java@revision-1");
        assertThatCode(() -> StoryContextResultValidator.validateImplementationPreparation(
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.ImplementationPreparation(
                        List.of(new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.ImplementationFile(
                                "backend/App.java", "retrieved", List.of(new com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef(
                                        "retrieved:backend/App.java@revision-1", "backend/App.java")))), List.of(), List.of()),
                allowed, Map.of("retrieved:backend/App.java@revision-1", "backend/App.java")));
        assertThat(((Map<?, ?>) snapshot.get("referenceMapping")).toString())
                .contains("retrieved:backend/App.java@revision-1");
    }

    @Test
    void rejectsCrossTaskCorrelationAndProjectBindingBeforeRetrieval() {
        AiTask task = task(snapshotWithGrounding());
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);

        var mismatchedCorrelation = new InvestigationActionRequest("SEARCH_EVIDENCE", "action-1", "devlog", REVISION,
                "find source", new InvestigationActionRequest.Limits(1, 10_000, 2_000), "find", "backend", null,
                "story-context-agent-protocol/v2", EXECUTION_ID, TASK_ID, UUID.randomUUID(), "openai", BINDING_ID, 0);
        assertThatThrownBy(() -> service().execute(principal(), TASK_ID, mismatchedCorrelation))
                .isInstanceOf(com.hopeful117.devlogai.shared.exception.ConflictException.class);

        var mismatchedProject = new InvestigationActionRequest("SEARCH_EVIDENCE", "action-2", "other-project", REVISION,
                "find source", new InvestigationActionRequest.Limits(1, 10_000, 2_000), "find", "backend", null,
                "story-context-agent-protocol/v2", EXECUTION_ID, TASK_ID, CORRELATION_ID, "openai", BINDING_ID, 0);
        assertThat(service().execute(principal(), TASK_ID, mismatchedProject).result())
                .containsEntry("rejectionCode", "SCOPE_MISMATCH");
        Map<String, Object> investigation = (Map<String, Object>) task.getContextSnapshot().get("investigation");
        Map<String, Object> usage = (Map<String, Object>) investigation.get("usage");
        assertThat(investigation).containsEntry("accountedProviderGenerationCount", 1);
        assertThat(usage).containsEntry("providerOutputTokens", 0)
                .containsEntry("elapsedMillis", 0L);
        verifyNoInteractions(executor);
    }

    @Test
    void doesNotAddNonCitableRetrievedEvidenceToFinalGrounding() {
        AiTask task = task(snapshotWithGrounding());
        when(repository.findByIdForUpdate(TASK_ID)).thenReturn(Optional.of(task));
        when(authorization.isAuthorized(any(), eq(PROJECT_ID), eq("MCP_STORY_AGENT_EXECUTE"))).thenReturn(true);
        when(executor.execute(eq(PROJECT_ID), eq(STORY_ID), any(), any(), any(), any(), any()))
                .thenReturn(result(false, "retrieved:backend/App.java@revision-1"));

        service().execute(principal(), TASK_ID, request());

        Map<String, Object> contract = (Map<String, Object>) task.getContextSnapshot().get("groundingContract");
        var allowed = AnalyzeStoryContextUseCase.allowedGroundingReferences(contract, task);
        assertThat(allowed).doesNotContain("retrieved:backend/App.java@revision-1");
        assertThatThrownBy(() -> StoryContextResultValidator.validateImplementationPreparation(
                new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.ImplementationPreparation(
                        List.of(new com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult.ImplementationFile(
                                "backend/App.java", "not citable", List.of(new com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef(
                                        "retrieved:backend/App.java@revision-1", "backend/App.java")))), List.of(), List.of()),
                allowed, Map.of("retrieved:backend/App.java@revision-1", "backend/App.java")))
                .isInstanceOf(IllegalStateException.class);
    }

    private StoryContextInvestigationActionService service() {
        return new StoryContextInvestigationActionService(repository, authorization, executor,
                new ObjectMapper(), Clock.fixed(Instant.parse("2025-12-15T00:00:00Z"), ZoneOffset.UTC));
    }

    private AiTask task(Map<String, Object> snapshot) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        var analysis = new com.hopeful117.devlogai.analysis.entity.Analysis();
        analysis.setProject(project);
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setAnalysis(analysis);
        task.setTaskType(AiTaskType.STORY_CONTEXT_ANALYSIS);
        task.setStatus(AiTaskStatus.PROCESSING);
        task.setCorrelationId(CORRELATION_ID);
        task.setProvider("openai");
        task.setCredentialBindingId(BINDING_ID);
        if (snapshot.get("investigation") instanceof Map<?, ?> raw) {
            Map<String, Object> investigation = new java.util.LinkedHashMap<>((Map<String, Object>) raw);
            investigation.put("protocolVersion", "story-context-agent-protocol/v2");
            investigation.put("executionId", EXECUTION_ID.toString());
            investigation.put("parentTaskId", TASK_ID.toString());
            snapshot = new java.util.LinkedHashMap<>(snapshot);
            snapshot.put("investigation", investigation);
        }
        task.setCreatedAt(Instant.parse("2025-12-01T00:00:00Z"));
        task.setContextSnapshot(snapshot);
        task.setSelectedKnowledgeSnapshot(Map.of("freshness", freshness()));
        if (snapshot.get("referenceMapping") instanceof Map<?, ?> mapping) {
            task.setAiReferenceMappingSnapshot((Map<String, Object>) mapping);
        }
        return task;
    }

    private AuthenticatedPrincipal principal() {
        return new AuthenticatedPrincipal("user", com.hopeful117.devlogai.authorization.PrincipalKind.HUMAN, "test");
    }

    private InvestigationActionRequest request() {
        return requestWith("action-1", 0);
    }

    private InvestigationActionRequest requestWith(String actionId, int providerOutputTokens) {
        return requestWithQuery(actionId, "find source", providerOutputTokens);
    }

    private InvestigationActionRequest requestWithQuery(String actionId, String query, int providerOutputTokens) {
        return new InvestigationActionRequest("SEARCH_EVIDENCE", actionId, "devlog", REVISION,
                "find source", new InvestigationActionRequest.Limits(1, 10_000, 2_000), query, "backend", null,
                "story-context-agent-protocol/v2", EXECUTION_ID, TASK_ID, CORRELATION_ID, "openai", BINDING_ID,
                providerOutputTokens, 1, 7L);
    }

    private InvestigationCapabilityResult result(boolean citable, String reference) {
        return new InvestigationCapabilityResult(PROJECT_ID, STORY_ID, "action-1",
                InvestigationActionKind.SEARCH_EVIDENCE, "devlog", REVISION,
                InvestigationCapabilityResult.Outcome.RESULTS,
                List.of(new InvestigationCapabilityResult.Evidence(reference, "SOURCE_FILE", "backend/App.java",
                        REVISION, "REPOSITORY", "app-1", "bounded summary",
                        com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier.TECHNICAL_EVIDENCE,
                        citable, null, null, null)), null, null);
    }

    private Map<String, Object> snapshotWithGrounding() {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>(Map.of(
                "scope", scope(), "freshness", freshness(), "investigation", Map.of(
                        "initialized", true, "scope", Map.of("projectSlug", "devlog", "revision", REVISION,
                                "allowedPathPrefixes", List.of("backend")), "budget", budget(), "usage", usage())));
        snapshot.put("groundingContract", new java.util.LinkedHashMap<>(Map.of(
                "groundingContractVersion", "story-context-grounding/v1",
                "allowedGroundingReferences", List.of())));
        snapshot.put("referenceMapping", new java.util.LinkedHashMap<>(Map.of(
                "contractVersion", "AI_REFERENCE_MAPPING_V1", "mappingDigest", "EMPTY",
                "bindings", List.of(), "architectureKnowledgeReferences", List.of())));
        return snapshot;
    }

    private Map<String, Object> scope() { return Map.of("projectSlug", "devlog", "storyId", STORY_ID.toString(), "files", List.of("backend")); }
    private Map<String, Object> freshness() { return Map.of("sourceRevision", Map.of("revision", REVISION)); }
    private Map<String, Object> budget() { return Map.of("maximumSteps", 2, "maximumRetrievalCalls", 2, "maximumEvidenceItems", 5, "maximumBytes", 20_000, "maximumTokens", 5_000, "maximumElapsedMillis", 30_000, "maximumProviderOutputTokens", 1_000); }
    private Map<String, Object> usage() { return Map.of("steps", 0, "retrievalCalls", 0, "evidenceItems", 0, "bytes", 0, "tokens", 0, "elapsedMillis", 0, "providerOutputTokens", 0); }
}
