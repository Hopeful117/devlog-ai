package com.hopeful117.devlogai.retrieval.listener;

import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.entity.RetrievalIndexingQueueEntry;
import com.hopeful117.devlogai.retrieval.event.DecisionChangedEvent;
import com.hopeful117.devlogai.retrieval.event.InsightAcceptedEvent;
import com.hopeful117.devlogai.retrieval.event.StoryStatusChangedEvent;
import com.hopeful117.devlogai.retrieval.repository.RetrievalIndexingQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RetrievalIndexingEventListenerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private RetrievalIndexingQueueRepository queueRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Test
    void insightAccepted_enqueuesPendingEntry() {
        UUID projectId = insertProject();
        UUID insightId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new InsightAcceptedEvent(projectId, insightId));
            return null;
        });

        List<RetrievalIndexingQueueEntry> entries =
                queueRepository.findTop10ByStatusOrderByQueuedAtAsc("PENDING");
        assertThat(entries)
                .filteredOn(e -> e.getSourceId().equals(insightId))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.getProjectId()).isEqualTo(projectId);
                    assertThat(e.getSourceType()).isEqualTo("INSIGHT");
                    assertThat(e.getStatus()).isEqualTo("PENDING");
                    assertThat(e.getAttempts()).isZero();
                });
    }

    @Test
    void storyStatusChanged_enqueuesPendingEntry() {
        UUID projectId = insertProject();
        UUID storyId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new StoryStatusChangedEvent(projectId, storyId));
            return null;
        });

        List<RetrievalIndexingQueueEntry> entries =
                queueRepository.findTop10ByStatusOrderByQueuedAtAsc("PENDING");
        assertThat(entries)
                .filteredOn(e -> e.getSourceId().equals(storyId))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.getSourceType()).isEqualTo("STORY");
                    assertThat(e.getStatus()).isEqualTo("PENDING");
                });
    }

    @Test
    void decisionChanged_enqueuesPendingEntry() {
        UUID projectId = insertProject();
        UUID decisionId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new DecisionChangedEvent(projectId, decisionId));
            return null;
        });

        List<RetrievalIndexingQueueEntry> entries =
                queueRepository.findTop10ByStatusOrderByQueuedAtAsc("PENDING");
        assertThat(entries)
                .filteredOn(e -> e.getSourceId().equals(decisionId))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.getSourceType()).isEqualTo("DECISION");
                    assertThat(e.getStatus()).isEqualTo("PENDING");
                });
    }

    @Test
    void multipleEvents_allEnqueued() {
        UUID projectId = insertProject();
        UUID insightId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new InsightAcceptedEvent(projectId, insightId));
            eventPublisher.publishEvent(new StoryStatusChangedEvent(projectId, storyId));
            return null;
        });

        List<RetrievalIndexingQueueEntry> entries =
                queueRepository.findAll().stream()
                        .filter(e -> e.getProjectId().equals(projectId))
                        .toList();
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(RetrievalIndexingQueueEntry::getSourceType)
                .containsExactlyInAnyOrder("INSIGHT", "STORY");
    }

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Listener Test " + id, "listener-test-" + id, now, now);
        return id;
    }
}
