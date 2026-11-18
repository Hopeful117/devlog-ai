package com.hopeful117.devlogai.retrieval.job;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;
import com.hopeful117.devlogai.retrieval.factory.IndexableDocumentFactory;
import com.hopeful117.devlogai.retrieval.service.IndexingResult;
import com.hopeful117.devlogai.retrieval.service.RetrievalDocumentIndexingService;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hopeful117.devlogai.retrieval.config.BackfillProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalDocumentBackfillJob {

    private final BackfillProperties backfillProperties;
    private final ProjectRepository projectRepository;
    private final InsightRepository insightRepository;
    private final EngineeringStoryRepository storyRepository;
    private final DecisionRepository decisionRepository;
    private final RetrievalDocumentIndexingService indexingService;

    @EventListener(ApplicationReadyEvent.class)
    public void runBackfill() {
        if (!backfillProperties.enabled()) {
            log.debug("Retrieval backfill is disabled (devlog.retrieval.backfill.enabled=false)");
            return;
        }
        log.info("Starting retrieval document backfill");
        int totalIndexed = 0;
        int totalSkipped = 0;
        int totalFailed = 0;
        try {
            List<Project> projects = projectRepository.findAll();
            for (Project project : projects) {
                BackfillStats stats = backfillProject(project.getId());
                totalIndexed += stats.indexed();
                totalSkipped += stats.skipped();
                totalFailed += stats.failed();
            }
        } catch (Exception e) {
            log.error("Retrieval backfill encountered an unexpected error", e);
        }
        log.info("Retrieval document backfill completed indexed={} skipped={} failed={}",
                totalIndexed, totalSkipped, totalFailed);
    }

    private BackfillStats backfillProject(UUID projectId) {
        int indexed = 0;
        int skipped = 0;
        int failed = 0;

        List<IndexableDocument> documents = new ArrayList<>();
        documents.addAll(insightsForProject(projectId));
        documents.addAll(storiesForProject(projectId));
        documents.addAll(decisionsForProject(projectId));

        int batchSize = backfillProperties.batchSize();
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<IndexableDocument> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            for (IndexableDocument doc : batch) {
                try {
                    IndexingResult result = indexingService.index(doc);
                    switch (result) {
                        case INDEXED -> indexed++;
                        case SKIPPED -> skipped++;
                        case EMBEDDING_FAILED -> failed++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to index document sourceType={} projectId={}", doc.sourceType(), projectId, e);
                    failed++;
                }
            }
        }
        log.info("Backfill project={} indexed={} skipped={} failed={}", projectId, indexed, skipped, failed);
        return new BackfillStats(indexed, skipped, failed);
    }

    private List<IndexableDocument> insightsForProject(UUID projectId) {
        return insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE))
                .stream()
                .map(this::toInsightDocument)
                .toList();
    }

    private List<IndexableDocument> storiesForProject(UUID projectId) {
        return storyRepository.findByProject_Id(projectId)
                .stream()
                .map(this::toStoryDocument)
                .toList();
    }

    private List<IndexableDocument> decisionsForProject(UUID projectId) {
        return decisionRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toDecisionDocument)
                .toList();
    }

    private IndexableDocument toInsightDocument(Insight insight) {
        return IndexableDocumentFactory.fromInsight(insight);
    }

    private IndexableDocument toStoryDocument(EngineeringStory story) {
        return IndexableDocumentFactory.fromStory(story);
    }

    private IndexableDocument toDecisionDocument(Decision decision) {
        return IndexableDocumentFactory.fromDecision(decision);
    }

    private record BackfillStats(int indexed, int skipped, int failed) {}
}
