package com.hopeful117.devlogai.retrieval.listener;

import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;
import com.hopeful117.devlogai.retrieval.entity.RetrievalIndexingQueueEntry;
import com.hopeful117.devlogai.retrieval.event.AdrDocumentProcessedEvent;
import com.hopeful117.devlogai.retrieval.event.DecisionChangedEvent;
import com.hopeful117.devlogai.retrieval.event.InsightAcceptedEvent;
import com.hopeful117.devlogai.retrieval.event.StoryStatusChangedEvent;
import com.hopeful117.devlogai.retrieval.repository.RetrievalIndexingQueueRepository;
import com.hopeful117.devlogai.retrieval.service.RetrievalDocumentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalIndexingEventListener {

    private final RetrievalIndexingQueueRepository queueRepository;
    private final RetrievalDocumentIndexingService indexingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInsightAccepted(InsightAcceptedEvent event) {
        enqueue(event.projectId(), "INSIGHT", event.insightId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStoryStatusChanged(StoryStatusChangedEvent event) {
        enqueue(event.projectId(), "STORY", event.storyId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDecisionChanged(DecisionChangedEvent event) {
        enqueue(event.projectId(), "DECISION", event.decisionId());
    }

    /**
     * ADR documents have no DB entity; content is passed inline from DocumentBodyCollector.
     * Uses a plain @EventListener (not transactional) since the collector may run outside a transaction.
     */
    @EventListener
    public void onAdrDocumentProcessed(AdrDocumentProcessedEvent event) {
        try {
            indexingService.index(adrIndexableDocument(event));
        } catch (Exception e) {
            log.warn("Failed to index ADR document path={} projectId={}: {}",
                    event.path(), event.projectId(), e.getMessage());
        }
    }

    private void enqueue(UUID projectId, String sourceType, UUID sourceId) {
        queueRepository.save(RetrievalIndexingQueueEntry.builder()
                .projectId(projectId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .queuedAt(Instant.now())
                .status("PENDING")
                .attempts(0)
                .build());
    }

    private IndexableDocument adrIndexableDocument(AdrDocumentProcessedEvent event) {
        return new IndexableDocument() {
            @Override public UUID projectId() { return event.projectId(); }
            @Override public String sourceType() { return "ADR_DOCUMENT"; }
            @Override public UUID sourceId() { return event.sourceId(); }
            @Override public String sourceRevision() { return null; }
            @Override public String trustTier() { return "TECHNICAL_EVIDENCE"; }
            @Override public String status() { return "ACTIVE"; }
            @Override public String title() { return event.title(); }
            @Override public String content() { return event.content(); }
            @Override public Map<String, Object> metadata() { return Map.of("path", event.path()); }
        };
    }
}
