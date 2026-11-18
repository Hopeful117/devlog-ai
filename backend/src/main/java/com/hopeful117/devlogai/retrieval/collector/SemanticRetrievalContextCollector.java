package com.hopeful117.devlogai.retrieval.collector;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.collector.EvidenceFactory;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.config.EmbeddingClientProperties;
import com.hopeful117.devlogai.retrieval.entity.RetrievalDocument;
import com.hopeful117.devlogai.retrieval.repository.RetrievalDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Order(50)
public class SemanticRetrievalContextCollector implements RepositoryContextCollector {

    private static final Logger log = LoggerFactory.getLogger(SemanticRetrievalContextCollector.class);

    private final EmbeddingClient embeddingClient;
    private final EmbeddingClientProperties embeddingClientProperties;
    private final RetrievalDocumentRepository retrievalDocumentRepository;
    private final EvidenceFactory evidenceFactory;

    @Value("${devlog.retrieval.semantic.enabled:false}")
    private boolean enabled;

    @Value("${devlog.retrieval.semantic.max-results:10}")
    private int maxResults;

    @Value("${devlog.retrieval.semantic.max-distance:0.4}")
    private float maxDistance;

    public SemanticRetrievalContextCollector(
            EmbeddingClient embeddingClient,
            EmbeddingClientProperties embeddingClientProperties,
            RetrievalDocumentRepository retrievalDocumentRepository,
            EvidenceFactory evidenceFactory) {
        this.embeddingClient = embeddingClient;
        this.embeddingClientProperties = embeddingClientProperties;
        this.retrievalDocumentRepository = retrievalDocumentRepository;
        this.evidenceFactory = evidenceFactory;
    }

    @Override
    public String collectorId() { return "semantic-retrieval"; }

    @Override
    public String collectorVersion() { return "v1"; }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        if (!enabled) {
            return List.of();
        }

        String queryText = buildQueryText(request);
        if (queryText.isBlank()) {
            return List.of();
        }

        UUID projectId = request.analysisContext().project().id();

        float[] queryEmbedding;
        try {
            var response = embeddingClient.embed(
                    List.of(queryText), embeddingClientProperties.defaultModel());
            if (response.isEmpty() || response.get().embeddings().isEmpty()) {
                log.warn("[{}] embedding returned empty response for project {}", collectorId(), projectId);
                return List.of();
            }
            queryEmbedding = response.get().embeddings().get(0);
        } catch (Exception e) {
            log.warn("[{}] embedding call failed for project {}: {}", collectorId(), projectId, e.getMessage());
            return List.of();
        }

        List<RetrievalDocument> candidates;
        try {
            candidates = retrievalDocumentRepository.findByProjectIdAndSimilarity(
                    projectId, queryEmbedding, maxResults, maxDistance);
        } catch (Exception e) {
            log.warn("[{}] pgvector similarity query failed for project {}: {}", collectorId(), projectId, e.getMessage());
            return List.of();
        }

        List<RepositoryEvidence> results = new ArrayList<>();
        for (RetrievalDocument doc : candidates) {
            RepositoryEvidence evidence = evidenceFactory.create(
                    metadata(),
                    new EvidenceFactory.EvidenceInput(
                            layerFor(doc.getSourceType()),
                            doc.getSourceType(),
                            doc.getSourceType().toLowerCase() + ":" + doc.getSourceId(),
                            buildSummary(doc, request.budget().maximumSummaryCharacters()),
                            doc.getIndexedAt(),
                            List.of(),
                            null, null,
                            doc.getSourceId().toString()),
                    request.budget().maximumSummaryCharacters());
            evidence = evidence.withExtractionMetadata(java.util.Map.of(
                    "collectorId", collectorId(),
                    "collectorVersion", collectorVersion(),
                    "trustTier", doc.getTrustTier()));
            results.add(evidence);
        }
        return List.copyOf(results);
    }

    private String buildQueryText(ContextRequest request) {
        StringBuilder sb = new StringBuilder();
        String objective = request.intent().objective();
        if (objective != null && !objective.isBlank()) {
            sb.append(objective);
        }
        String focus = request.guidance().focus();
        if (focus != null && !focus.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(focus);
        }
        request.guidance().priorities().forEach(p -> {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(p);
        });
        return sb.toString().trim();
    }

    private String buildSummary(RetrievalDocument doc, int maxChars) {
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            return doc.getTitle();
        }
        String combined = doc.getTitle() + " — " + content;
        return combined.length() <= maxChars ? combined : combined.substring(0, maxChars);
    }

    private RepositoryContextLayer layerFor(String sourceType) {
        return switch (sourceType) {
            case "INSIGHT" -> RepositoryContextLayer.VALIDATED_INSIGHT;
            case "STORY" -> RepositoryContextLayer.ROADMAP;
            case "ADR", "DECISION" -> RepositoryContextLayer.ADR;
            default -> RepositoryContextLayer.PROJECT_DOCUMENTATION;
        };
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(collectorId(), collectorVersion(), "SEMANTIC_RETRIEVAL");
    }
}
