package com.hopeful117.devlogai.retrieval.collector;

import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.collector.EvidenceFactory;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Order(45)
public class LexicalKnowledgeSearchCollector implements RepositoryContextCollector {

    private static final Logger log = LoggerFactory.getLogger(LexicalKnowledgeSearchCollector.class);

    private final InsightRepository insightRepository;
    private final EngineeringStoryRepository storyRepository;
    private final KnowledgeEventRepository knowledgeEventRepository;
    private final EvidenceFactory evidenceFactory;

    @Value("${devlog.retrieval.lexical-fulltext.enabled:false}")
    private boolean enabled;

    @Value("${devlog.retrieval.lexical-fulltext.max-results:10}")
    private int maxResults;

    public LexicalKnowledgeSearchCollector(
            InsightRepository insightRepository,
            EngineeringStoryRepository storyRepository,
            KnowledgeEventRepository knowledgeEventRepository,
            EvidenceFactory evidenceFactory) {
        this.insightRepository = insightRepository;
        this.storyRepository = storyRepository;
        this.knowledgeEventRepository = knowledgeEventRepository;
        this.evidenceFactory = evidenceFactory;
    }

    @Override
    public String collectorId() { return "lexical-knowledge-search"; }

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
        List<RepositoryEvidence> results = new ArrayList<>();

        try {
            insightRepository.findByProjectIdAndFullText(projectId, queryText, maxResults)
                    .forEach(insight -> results.add(evidenceFactory.create(
                            metadata(),
                            new EvidenceFactory.EvidenceInput(
                                    RepositoryContextLayer.VALIDATED_INSIGHT,
                                    "INSIGHT",
                                    "insight:" + insight.getId(),
                                    insight.getTitle() + " — " + insight.getContent(),
                                    insight.getCreatedAt(),
                                    List.of(),
                                    null, null,
                                    insight.getId().toString()),
                            request.budget().maximumSummaryCharacters())));
        } catch (Exception e) {
            log.warn("[{}] full-text search on insights failed: {}", collectorId(), e.getMessage());
        }

        try {
            storyRepository.findByProjectIdAndFullText(projectId, queryText, maxResults)
                    .forEach(story -> results.add(evidenceFactory.create(
                            metadata(),
                            new EvidenceFactory.EvidenceInput(
                                    RepositoryContextLayer.ROADMAP,
                                    "ENGINEERING_STORY",
                                    "story:" + story.getId(),
                                    story.getTitle(),
                                    story.getCreatedAt(),
                                    List.of(),
                                    "ENGINEERING_STORY", story.getStoryPath(),
                                    story.getId().toString()),
                            request.budget().maximumSummaryCharacters())));
        } catch (Exception e) {
            log.warn("[{}] full-text search on stories failed: {}", collectorId(), e.getMessage());
        }

        try {
            knowledgeEventRepository.findByProjectIdAndFullText(projectId, queryText, maxResults)
                    .forEach(event -> results.add(evidenceFactory.create(
                            metadata(),
                            new EvidenceFactory.EvidenceInput(
                                    RepositoryContextLayer.ADR,
                                    "DECISION",
                                    "decision:" + event.getId(),
                                    event.getTitle() + (event.getDescription() != null
                                            ? " — " + event.getDescription() : ""),
                                    event.getCreatedAt(),
                                    List.of(),
                                    null, null,
                                    event.getId().toString()),
                            request.budget().maximumSummaryCharacters())));
        } catch (Exception e) {
            log.warn("[{}] full-text search on knowledge_events failed: {}", collectorId(), e.getMessage());
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

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(collectorId(), collectorVersion(), "LEXICAL_SEARCH");
    }
}
