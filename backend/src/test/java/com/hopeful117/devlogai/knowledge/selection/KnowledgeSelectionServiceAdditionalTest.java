package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeSelectionServiceAdditionalTest {

    @Mock private AnalysisExecutionDiagnosticRepository diagnosticRepository;
    @Mock private InsightRepository insightRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private RepositoryContextService repositoryContextService;

    private KnowledgeSelectionServiceImpl createService() {
        return new KnowledgeSelectionServiceImpl(diagnosticRepository, insightRepository,
                objectMapper, repositoryContextService);
    }

    private IntentDefinition architectureIntent() {
        return new IntentDefinition("architecture-overview", "v1", "Overview",
                List.of(), List.of(), Map.of(), null);
    }

    private AnalysisContext.AnalysisSnapshot testAnalysis() {
        return new AnalysisContext.AnalysisSnapshot(
                UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW, "architecture-overview", "v1",
                AnalysisStatus.IN_PROGRESS, null, null, Instant.now());
    }

    private ProjectProfileResponse testProfile() {
        return new ProjectProfileResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "v1", "r1", Instant.now(), null, Map.of(),
                new ProjectProfileResponse.Completeness(ProfileCompletenessStatus.COMPLETE, true, false, 0, 0, 1, 0, 0),
                List.of(), "summary", List.of(), 0);
    }

    private AnalysisContext createMinimalContext(AnalysisContext.AnalysisSnapshot analysis) {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "Test", "test", "desc", ProjectStatus.ACTIVE),
                analysis, testProfile(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void mockRepositories(AnalysisContext context) throws Exception {
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(context.analysis().id())
                .collectionComplete(true).truncated(false)
                .warningCount(0).errorCount(0)
                .build();
        when(diagnosticRepository.findById(context.analysis().id())).thenReturn(Optional.of(diagnostic));
        when(insightRepository.findByProjectIdOrderByCreatedAtDesc(context.project().id())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        RepositoryContext repoContext = new RepositoryContext(
                "v1", null, List.of(), "v1", List.of(), List.of(),
                Map.of(), new RepositoryContext.ContextBudget(50, 200, 10, 10000),
                0, 0, 0, false, List.of(), List.of(), "digest");
        when(repositoryContextService.build(any(), any(), any(), anyList())).thenReturn(repoContext);
    }

    @Test
    void shouldSelectKnowledgeWithMinimalContext() throws Exception {
        var service = createService();
        var analysis = testAnalysis();
        var context = createMinimalContext(analysis);

        mockRepositories(context);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        assertNotNull(result);
        assertNotNull(result.selectionDigest());
        assertEquals(KnowledgeSelectionServiceImpl.VERSION, result.selectionMetadata().selectionVersion());
    }

    @Test
    void shouldFilterObservationsByBudget() throws Exception {
        var service = createService();
        var analysis = testAnalysis();
        List<AnalysisContext.ObservationSnapshot> observations = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            observations.add(new AnalysisContext.ObservationSnapshot(
                    UUID.randomUUID(), ObservationType.SPRING_BOOT_REST_APPLICATION,
                    "obs" + i, "RULE", "1", List.of(), Instant.now()));
        }
        var context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "Test", "test", "desc", ProjectStatus.ACTIVE),
                analysis, testProfile(),
                List.of(), observations, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        mockRepositories(context);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        assertTrue(result.selectedObservations().size() <= KnowledgeSelectionServiceImpl.BUDGET.maximumObservations());
    }

    @Test
    void shouldFilterFactsByContentDeduplication() throws Exception {
        var service = createService();
        var analysis = testAnalysis();
        List<AnalysisContext.FactSnapshot> facts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            facts.add(new AnalysisContext.FactSnapshot(
                    UUID.randomUUID(), FactType.SPRING_BOOT_DETECTED,
                    "duplicate content", "source", List.of("pom.xml"), Instant.now()));
        }
        var context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "Test", "test", "desc", ProjectStatus.ACTIVE),
                analysis, testProfile(),
                facts, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        mockRepositories(context);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        assertEquals(1, result.selectedFacts().size());
    }

    @Test
    void shouldSelectBoundedExistingArchitectureKnowledgeForArchitectureIntent() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(context.analysis().id())
                .collectionComplete(true).truncated(false)
                .warningCount(0).errorCount(0)
                .build();
        when(diagnosticRepository.findById(context.analysis().id())).thenReturn(Optional.of(diagnostic));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        RepositoryContext repoContext = new RepositoryContext(
                "v1", null, List.of(), "v1", List.of(), List.of(),
                Map.of(), new RepositoryContext.ContextBudget(50, 200, 10, 10000),
                0, 0, 0, false, List.of(), List.of(), "digest");
        when(repositoryContextService.build(any(), any(), any(), anyList())).thenReturn(repoContext);

        List<Insight> insights = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            insights.add(Insight.builder()
                    .id(UUID.randomUUID())
                    .analysis(com.hopeful117.devlogai.analysis.entity.Analysis.builder().id(UUID.randomUUID()).build())
                    .proposal(com.hopeful117.devlogai.proposal.entity.ValidatableProposal.builder().id(UUID.randomUUID()).build())
                    .type(InsightType.ARCHITECTURAL)
                    .severity(InsightSeverity.INFO)
                    .title("Architecture " + i)
                    .content("Content " + i)
                    .sourceType("ARCHITECTURE_DESCRIPTION")
                    .createdAt(Instant.now().minusSeconds(i))
                    .build());
        }
        when(insightRepository.findByProjectIdOrderByCreatedAtDesc(context.project().id())).thenReturn(insights);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);

        assertEquals(5, result.existingArchitectureKnowledge().size());
        assertEquals("ARCHITECTURE_DESCRIPTION",
                result.existingArchitectureKnowledge().getFirst().sourceType());
    }

    @Test
    void shouldIncludeUserGuidanceBoostInScoring() throws Exception {
        var service = createService();
        var context = createMinimalContext(testAnalysis());
        UserGuidance guidance = new UserGuidance("architecture", null, null, null, null,
                List.of("architecture", "docker"));

        mockRepositories(context);

        SelectedKnowledge result = service.select(context, architectureIntent(), guidance);

        assertNotNull(result);
        assertEquals(KnowledgeSelectionServiceImpl.VERSION, result.selectionMetadata().selectionVersion());
    }

    @Test
    void shouldReduceSelectedObservationsWhenSupportingFactClosureWouldOverflowBudget() throws Exception {
        var service = createService();
        var analysis = testAnalysis();

        List<AnalysisContext.FactSnapshot> facts = new ArrayList<>();
        List<AnalysisContext.ObservationSnapshot> observations = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            AnalysisContext.FactSnapshot factOne = new AnalysisContext.FactSnapshot(
                    UUID.randomUUID(), FactType.OTHER, "support-a-" + index,
                    "source", List.of("README.md"), Instant.now());
            AnalysisContext.FactSnapshot factTwo = new AnalysisContext.FactSnapshot(
                    UUID.randomUUID(), FactType.OTHER, "support-b-" + index,
                    "source", List.of("README.md"), Instant.now());
            facts.add(factOne);
            facts.add(factTwo);
            observations.add(new AnalysisContext.ObservationSnapshot(
                    UUID.randomUUID(),
                    ObservationType.SPRING_BOOT_REST_APPLICATION,
                    "obs" + index,
                    "RULE",
                    "1",
                    List.of(factOne.id(), factTwo.id()),
                    Instant.now()));
        }
        var context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "Test", "test", "desc", ProjectStatus.ACTIVE),
                analysis, testProfile(),
                facts, observations, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        mockRepositories(context);

        SelectedKnowledge result = service.select(context, architectureIntent(), null);
        Set<UUID> selectedFactIds = result.selectedFacts().stream()
                .map(AnalysisContext.FactSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(result.selectedFacts().size() <= KnowledgeSelectionServiceImpl.BUDGET.maximumFacts());
        assertTrue(result.selectedObservations().size() < KnowledgeSelectionServiceImpl.BUDGET.maximumObservations());
        assertTrue(result.selectedObservations().stream()
                .flatMap(observation -> observation.supportingFactIds().stream())
                .allMatch(selectedFactIds::contains));
    }

    @Test
    void shouldThrowWhenMandatoryKnowledgeUnavailable() {
        var service = createService();
        IntentDefinition intent = architectureIntent();
        assertThrows(IllegalStateException.class,
                () -> service.select(null, intent, null));
    }

    @Test
    void shouldThrowWhenIntentDoesNotMatchAnalysis() {
        var service = createService();
        var analysis = new AnalysisContext.AnalysisSnapshot(
                UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW, "different-intent", "v1",
                AnalysisStatus.IN_PROGRESS, null, null, Instant.now());
        var context = createMinimalContext(analysis);
        IntentDefinition intent = architectureIntent();

        assertThrows(IllegalArgumentException.class,
                () -> service.select(context, intent, null));
    }

    @Test
    void shouldThrowWhenDiagnosticsUnavailable() {
        var service = createService();
        var context = createMinimalContext(testAnalysis());

        when(insightRepository.findByProjectIdOrderByCreatedAtDesc(context.project().id())).thenReturn(List.of());
        RepositoryContext repoContext = new RepositoryContext(
                "v1", null, List.of(), "v1", List.of(), List.of(),
                Map.of(), new RepositoryContext.ContextBudget(50, 200, 10, 10000),
                0, 0, 0, false, List.of(), List.of(), "digest");
        when(repositoryContextService.build(any(), any(), any(), anyList())).thenReturn(repoContext);
        when(diagnosticRepository.findById(context.analysis().id())).thenReturn(Optional.empty());
        IntentDefinition intent = architectureIntent();

        assertThrows(IllegalStateException.class,
                () -> service.select(context, intent, null));
    }
}
