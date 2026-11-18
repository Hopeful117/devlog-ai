package com.hopeful117.devlogai.retrieval.job;

import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;
import com.hopeful117.devlogai.retrieval.entity.RetrievalIndexingQueueEntry;
import com.hopeful117.devlogai.retrieval.factory.IndexableDocumentFactory;
import com.hopeful117.devlogai.retrieval.repository.RetrievalIndexingQueueRepository;
import com.hopeful117.devlogai.retrieval.service.RetrievalDocumentIndexingService;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processes a single queue entry in its own transaction so that a failure
 * on one entry does not roll back updates on others in the same batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalIndexingQueueProcessorDelegate {

    private static final int MAX_ATTEMPTS = 3;

    private final RetrievalIndexingQueueRepository queueRepository;
    private final RetrievalDocumentIndexingService indexingService;
    private final InsightRepository insightRepository;
    private final EngineeringStoryRepository storyRepository;
    private final DecisionRepository decisionRepository;

    @Transactional
    public void processEntry(RetrievalIndexingQueueEntry entry) {
        try {
            Optional<IndexableDocument> doc = buildDocument(entry);
            if (doc.isEmpty()) {
                entry.setStatus("DONE");
            } else {
                indexingService.index(doc.get());
                entry.setStatus("DONE");
            }
        } catch (Exception e) {
            int attempts = entry.getAttempts() + 1;
            entry.setAttempts(attempts);
            entry.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
            if (attempts >= MAX_ATTEMPTS) {
                entry.setStatus("FAILED");
                log.warn("Queue entry marked FAILED after {} attempts sourceType={} sourceId={}",
                        MAX_ATTEMPTS, entry.getSourceType(), entry.getSourceId());
            }
        }
        queueRepository.save(entry);
    }

    private Optional<IndexableDocument> buildDocument(RetrievalIndexingQueueEntry entry) {
        return switch (entry.getSourceType()) {
            case "INSIGHT" -> insightRepository.findById(entry.getSourceId())
                    .filter(i -> InsightStatus.ACTIVE == i.getStatus())
                    .map(IndexableDocumentFactory::fromInsight);
            case "STORY" -> storyRepository.findById(entry.getSourceId())
                    .map(IndexableDocumentFactory::fromStory);
            case "DECISION" -> decisionRepository.findById(entry.getSourceId())
                    .map(IndexableDocumentFactory::fromDecision);
            default -> {
                log.warn("Unknown source type in indexing queue: {}", entry.getSourceType());
                yield Optional.empty();
            }
        };
    }
}
