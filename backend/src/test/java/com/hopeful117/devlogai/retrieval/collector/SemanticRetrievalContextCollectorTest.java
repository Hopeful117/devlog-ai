package com.hopeful117.devlogai.retrieval.collector;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.collector.EvidenceFactory;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import com.hopeful117.devlogai.retrieval.config.EmbeddingClientProperties;
import com.hopeful117.devlogai.retrieval.entity.RetrievalDocument;
import com.hopeful117.devlogai.retrieval.repository.RetrievalDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticRetrievalContextCollectorTest {

    private EmbeddingClient embeddingClient;
    private RetrievalDocumentRepository repository;
    private SemanticRetrievalContextCollector collector;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ANALYSIS_ID = UUID.randomUUID();
    private static final float[] QUERY_VECTOR = new float[]{0.1f, 0.2f, 0.3f};

    @BeforeEach
    void setUp() {
        embeddingClient = mock(EmbeddingClient.class);
        repository = mock(RetrievalDocumentRepository.class);
        EmbeddingClientProperties properties = new EmbeddingClientProperties(
                Duration.ofSeconds(30), "text-embedding-3-small");
        collector = new SemanticRetrievalContextCollector(
                embeddingClient, properties, repository, new EvidenceFactory());
        ReflectionTestUtils.setField(collector, "enabled", true);
        ReflectionTestUtils.setField(collector, "maxResults", 10);
        ReflectionTestUtils.setField(collector, "maxDistance", 0.4f);
    }

    // --- disabled flag ---

    @Test
    void disabledCollector_returnsEmptyWithoutCallingEmbeddingClient() {
        ReflectionTestUtils.setField(collector, "enabled", false);

        List<RepositoryEvidence> result = collector.collect(requestWith("decoupling strategy"));

        assertThat(result).isEmpty();
        verifyNoInteractions(embeddingClient);
        verifyNoInteractions(repository);
    }

    // --- embedding failure ---

    @Test
    void embeddingCallThrows_returnsEmptyList_noExceptionPropagated() {
        when(embeddingClient.embed(anyList(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> collector.collect(requestWith("test query")))
                .doesNotThrowAnyException();

        assertThat(collector.collect(requestWith("test query"))).isEmpty();
    }

    @Test
    void embeddingReturnsEmpty_returnsEmptyList() {
        when(embeddingClient.embed(anyList(), anyString())).thenReturn(Optional.empty());

        assertThat(collector.collect(requestWith("test query"))).isEmpty();
        verifyNoInteractions(repository);
    }

    // --- layer and kind mapping ---

    @Test
    void insightSourceType_mapsToValidatedInsightLayer() {
        stubEmbedding();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("INSIGHT", UUID.randomUUID(), "TRUSTED")));

        List<RepositoryEvidence> results = collector.collect(requestWith("architectural isolation"));

        assertThat(results).hasSize(1);
        RepositoryEvidence evidence = results.get(0);
        assertThat(evidence.layer()).isEqualTo(RepositoryContextLayer.VALIDATED_INSIGHT);
        assertThat(evidence.kind()).isEqualTo("INSIGHT");
    }

    @Test
    void storySourceType_mapsToRoadmapLayer() {
        stubEmbedding();
        UUID sourceId = UUID.randomUUID();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("STORY", sourceId, "HUMAN_AUTHORED")));

        List<RepositoryEvidence> results = collector.collect(requestWith("test"));

        assertThat(results.get(0).layer()).isEqualTo(RepositoryContextLayer.ROADMAP);
        assertThat(results.get(0).kind()).isEqualTo("STORY");
        assertThat(results.get(0).reference()).isEqualTo("story:" + sourceId);
    }

    @Test
    void adrSourceType_mapsToAdrLayer() {
        stubEmbedding();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("ADR", UUID.randomUUID(), "TRUSTED")));

        List<RepositoryEvidence> results = collector.collect(requestWith("test"));

        assertThat(results.get(0).layer()).isEqualTo(RepositoryContextLayer.ADR);
        assertThat(results.get(0).kind()).isEqualTo("ADR");
    }

    @Test
    void decisionSourceType_mapsToAdrLayer() {
        stubEmbedding();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("DECISION", UUID.randomUUID(), "TRUSTED")));

        List<RepositoryEvidence> results = collector.collect(requestWith("test"));

        assertThat(results.get(0).layer()).isEqualTo(RepositoryContextLayer.ADR);
        assertThat(results.get(0).kind()).isEqualTo("DECISION");
    }

    // --- provenance ---

    @Test
    void provenance_sourceTypeIsSemanticRetrieval() {
        stubEmbedding();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("INSIGHT", UUID.randomUUID(), "TRUSTED")));

        RepositoryEvidence evidence = collector.collect(requestWith("test")).get(0);

        assertThat(evidence.provenance().sourceType()).isEqualTo("SEMANTIC_RETRIEVAL");
    }

    // --- trust tier ---

    @Test
    void trustTier_inheritedFromRetrievalDocument() {
        stubEmbedding();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("INSIGHT", UUID.randomUUID(), "TRUSTED"),
                        documentOf("STORY", UUID.randomUUID(), "HUMAN_AUTHORED")));

        List<RepositoryEvidence> results = collector.collect(requestWith("test"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).extractionMetadata()).containsEntry("trustTier", "TRUSTED");
        assertThat(results.get(1).extractionMetadata()).containsEntry("trustTier", "HUMAN_AUTHORED");
    }

    // --- content included in summary ---

    @Test
    void summary_includesTitleAndContent() {
        stubEmbedding();
        UUID sourceId = UUID.randomUUID();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(documentOf("ADR", sourceId, "TRUSTED")));

        RepositoryEvidence evidence = collector.collect(requestWith("test")).get(0);

        assertThat(evidence.summary()).contains("Test document — ADR");
        assertThat(evidence.summary()).contains("Content for ADR");
    }

    @Test
    void summary_titleOnly_whenContentIsNull() {
        stubEmbedding();
        RetrievalDocument doc = RetrievalDocument.builder()
                .id(UUID.randomUUID()).projectId(PROJECT_ID)
                .sourceType("ADR").sourceId(UUID.randomUUID())
                .trustTier("TRUSTED").status("CURRENT")
                .title("Title only").content(null)
                .contentHash("hash").indexedAt(Instant.now()).metadata(Map.of())
                .build();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenReturn(List.of(doc));

        RepositoryEvidence evidence = collector.collect(requestWith("test")).get(0);

        assertThat(evidence.summary()).isEqualTo("Title only");
    }

    // --- pgvector failure ---

    @Test
    void pgvectorQueryThrows_returnsEmptyList_noExceptionPropagated() {
        stubEmbedding();
        when(repository.findByProjectIdAndSimilarity(any(), any(), anyInt(), anyFloat()))
                .thenThrow(new RuntimeException("pgvector error"));

        assertThatCode(() -> collector.collect(requestWith("test"))).doesNotThrowAnyException();
        assertThat(collector.collect(requestWith("test"))).isEmpty();
    }

    // --- helpers ---

    private void stubEmbedding() {
        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(QUERY_VECTOR), "text-embedding-3-small", 3)));
    }

    private RetrievalDocument documentOf(String sourceType, UUID sourceId, String trustTier) {
        return RetrievalDocument.builder()
                .id(UUID.randomUUID())
                .projectId(PROJECT_ID)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .trustTier(trustTier)
                .status("CURRENT")
                .title("Test document — " + sourceType)
                .content("Content for " + sourceType)
                .contentHash("hash-" + UUID.randomUUID())
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build();
    }

    private ContextRequest requestWith(String objective) {
        AnalysisContext analysisContext = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(PROJECT_ID, "Test Project",
                        "test-project", "A test project", ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(ANALYSIS_ID,
                        AnalysisType.ARCHITECTURE_REVIEW, "test-intent", "v1",
                        AnalysisStatus.IN_PROGRESS, Instant.now(), null, Instant.EPOCH),
                null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

        IntentDefinition intent = new IntentDefinition(
                "test-intent", "v1", objective,
                List.of(InsightType.ARCHITECTURE_DESCRIPTION),
                List.of("grounded"), Map.of("type", "object"),
                "test-prompt", List.of("architecture-v1"));

        UserGuidance guidance = new UserGuidance(null, null, null, null, null, List.of());

        return new ContextRequest(
                analysisContext, intent, guidance, List.of(),
                new ContextPlan("test-v1", List.of(), Map.of(), List.of(), 1, List.of()),
                new RepositoryContext.ContextBudget(20, 500, 10, 2000));
    }
}
