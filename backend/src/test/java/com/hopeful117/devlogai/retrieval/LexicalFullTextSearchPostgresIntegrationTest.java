package com.hopeful117.devlogai.retrieval;

import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LexicalFullTextSearchPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private InsightRepository insightRepository;

    @Autowired
    private EngineeringStoryRepository storyRepository;

    @Autowired
    private KnowledgeEventRepository knowledgeEventRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // --- insights full-text search ---

    @Test
    void insightFullTextSearchReturnsStemMatchedResults() {
        UUID projectId = insertProject();
        UUID insightId = insertInsight(projectId,
                "Monorepo architecture boundary",
                "Java Core owns deterministic context construction.");

        var results = insightRepository.findByProjectIdAndFullText(
                projectId, "architecture", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(insightId);
    }

    @Test
    void insightFullTextSearchRespectsProjectIsolation() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        insertInsight(projectA, "Bounded context design", "DDD principles applied.");
        insertInsight(projectB, "Bounded context implementation", "Spring modules used.");

        var fromB = insightRepository.findByProjectIdAndFullText(
                projectB, "bounded context", 10);

        assertThat(fromB).hasSize(1);
        assertThat(fromB.get(0).getTitle()).contains("implementation");
    }

    @Test
    void insightFullTextSearchReturnsEmptyWhenNoMatch() {
        UUID projectId = insertProject();
        insertInsight(projectId, "Event sourcing", "CQRS pattern.");

        var results = insightRepository.findByProjectIdAndFullText(
                projectId, "kubernetes", 10);

        assertThat(results).isEmpty();
    }

    // --- stories full-text search ---

    @Test
    void storyFullTextSearchReturnsStemMatchedResults() {
        UUID projectId = insertProject();
        UUID storyId = insertStory(projectId, "Embedding pipeline integration");

        var results = storyRepository.findByProjectIdAndFullText(
                projectId, "embedding", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(storyId);
    }

    @Test
    void storyFullTextSearchRespectsProjectIsolation() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        insertStory(projectA, "Vector similarity search");
        UUID storyBId = insertStory(projectB, "Vector index optimization");

        var fromB = storyRepository.findByProjectIdAndFullText(projectB, "vector", 10);

        assertThat(fromB).hasSize(1);
        assertThat(fromB.get(0).getId()).isEqualTo(storyBId);
    }

    // --- knowledge_events full-text search ---

    @Test
    void knowledgeEventFullTextSearchReturnsStemMatchedResults() {
        UUID projectId = insertProject();
        UUID eventId = insertKnowledgeEvent(projectId,
                "ADR-001 adopted",
                "Hexagonal architecture decision recorded.");

        var results = knowledgeEventRepository.findByProjectIdAndFullText(
                projectId, "hexagonal architecture", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(eventId);
    }

    @Test
    void knowledgeEventFullTextSearchRespectsProjectIsolation() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        insertKnowledgeEvent(projectA, "Trust tier model", "Insight trust tiers defined.");
        UUID eventBId = insertKnowledgeEvent(projectB, "Trust tier validation", "Validation rules set.");

        var fromB = knowledgeEventRepository.findByProjectIdAndFullText(
                projectB, "trust tier", 10);

        assertThat(fromB).hasSize(1);
        assertThat(fromB.get(0).getId()).isEqualTo(eventBId);
    }

    // --- helpers ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "FTS Test " + id, "fts-test-" + id, now, now);
        return id;
    }

    private UUID insertAnalysis(UUID projectId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO analyses
                    (id, project_id, type, status, intent_id, intent_version,
                     started_at, completed_at, created_at, updated_at)
                VALUES (?, ?, 'ARCHITECTURE_REVIEW', 'COMPLETED',
                        'describe-project', 'v1', ?, ?, ?, ?)
                """, id, projectId, now.minusMinutes(1), now, now.minusMinutes(2), now);
        return id;
    }

    private UUID insertInsight(UUID projectId, String title, String content) {
        UUID analysisId = insertAnalysis(projectId);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO insights
                    (id, project_id, analysis_id, title, content, type, severity, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ARCHITECTURAL', 'INFO', ?, ?)
                """, id, projectId, analysisId, title, content, now, now);
        return id;
    }

    private UUID insertStory(UUID projectId, String title) {
        UUID id = UUID.randomUUID();
        int storyNumber = (int) (Math.random() * 100000);
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO engineering_stories
                    (id, project_id, title, story_number, story_path, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'REGISTERED', ?, ?)
                """, id, projectId, title, storyNumber,
                "docs/stories/" + storyNumber + "-test/story.md", now, now);
        return id;
    }

    private UUID insertKnowledgeEvent(UUID projectId, String title, String description) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO knowledge_events
                    (id, project_id, title, description, type, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ARCHITECTURE', ?, ?)
                """, id, projectId, title, description, now, now);
        return id;
    }
}
