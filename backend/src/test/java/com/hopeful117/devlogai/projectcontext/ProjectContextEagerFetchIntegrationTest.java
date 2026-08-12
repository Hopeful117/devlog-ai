package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards against the {@code LazyInitializationException} that surfaced during
 * architecture-review context building: {@code ProjectContextProviderImpl}
 * reads lazy {@code EngineeringEvent.source}/{@code proposal} and
 * {@code EngineeringStory.project} associations outside any transaction (OSIV
 * disabled in production). The repositories must eagerly fetch those
 * associations so they remain accessible after the loading transaction closes,
 * regardless of the thread that consumes them.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@Testcontainers
class ProjectContextEagerFetchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProjectContextProvider projectContextProvider;

    @Autowired
    private EngineeringEventRepository engineeringEventRepository;

    @Autowired
    private EngineeringStoryRepository engineeringStoryRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private FactRepository factRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void engineeringEventAssociationsSurviveOutsideTheLoadingTransaction() throws Exception {
        UUID project = UUID.randomUUID();
        insertProject(project, "Context project", "context-project");
        UUID source = insertSource(project, "Context source");
        UUID analysis = insertAnalysis(project, source);
        UUID proposal = insertProposal(project, analysis);
        UUID validation = insertValidation(proposal);
        insertEngineeringEvent(project, analysis, source, proposal, validation);

        var events = engineeringEventRepository
                .findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
                        project, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, events.size());

        FutureTask<Integer> task = new FutureTask<>(() -> {
            var event = events.get(0);
            return event.getSource().getName().hashCode()
                    ^ event.getProposal().getType().name().hashCode();
        });
        Thread worker = Thread.startVirtualThread(task);
        assertNotNull(task.get(5, TimeUnit.SECONDS));
        worker.join();
    }

    @Test
    void engineeringStoryProjectAssociationSurvivesOutsideTheLoadingTransaction() throws Exception {
        UUID project = UUID.randomUUID();
        insertProject(project, "Story context project", "story-context-project");
        insertEngineeringStory(project, 1);

        var stories = engineeringStoryRepository.findByProject_IdOrderByCreatedAtDesc(project);
        assertEquals(1, stories.size());

        FutureTask<Integer> task = new FutureTask<>(() -> stories.get(0).getProject().getName()
                .hashCode());
        Thread worker = Thread.startVirtualThread(task);
        assertNotNull(task.get(5, TimeUnit.SECONDS));
        worker.join();
    }

    @Test
    void completeProjectContextIsBuildable() {
        UUID project = UUID.randomUUID();
        insertProject(project, "Full context project", "full-context-project");
        UUID source = insertSource(project, "Full context source");
        UUID analysis = insertAnalysis(project, source);
        UUID proposal = insertProposal(project, analysis);
        UUID validation = insertValidation(proposal);
        insertEngineeringEvent(project, analysis, source, proposal, validation);
        insertEngineeringStory(project, 1);
        insertProfile(project, analysis);

        ProjectContextSnapshot snapshot = projectContextProvider.build(project);

        assertNotNull(snapshot);
        assertEquals(1, snapshot.validatedEngineeringEvents().size());
        assertEquals(1, snapshot.engineeringStories().size());
    }

    @Test
    void factEvidenceReferencesSurviveOutsideTheLoadingTransaction() throws Exception {
        UUID project = UUID.randomUUID();
        insertProject(project, "Fact context project", "fact-context-project");
        UUID source = insertSource(project, "Fact context source");
        UUID analysis = insertAnalysis(project, source);
        UUID fact = insertFact(analysis);

        var facts = factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                analysis, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, facts.size());

        FutureTask<Integer> task = new FutureTask<>(
                () -> facts.get(0).getEvidenceReferences().size());
        Thread worker = Thread.startVirtualThread(task);
        Integer size = task.get(5, TimeUnit.SECONDS);
        worker.join();

        assertEquals(2, size);
    }

    @Test
    void observationSupportingFactsSurviveOutsideTheLoadingTransaction() throws Exception {
        UUID project = UUID.randomUUID();
        insertProject(project, "Observation context project", "observation-context-project");
        UUID source = insertSource(project, "Observation context source");
        UUID analysis = insertAnalysis(project, source);
        UUID fact = insertFact(analysis);
        UUID observation = insertObservation(analysis, fact);

        var observations = observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                analysis, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, observations.size());

        FutureTask<Integer> task = new FutureTask<>(
                () -> observations.get(0).getSupportingFacts().size());
        Thread worker = Thread.startVirtualThread(task);
        Integer size = task.get(5, TimeUnit.SECONDS);
        worker.join();

        assertEquals(1, size);
    }

    private void insertProject(UUID id, String name, String slug) {
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, name, slug, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private UUID insertSource(UUID projectId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into sources
                    (id, project_id, type, name, repository_url, default_branch, provider, active,
                     created_at, updated_at)
                values (?, ?, 'GIT_REPOSITORY', ?, 'https://example.test/repository.git', 'main',
                        'GITHUB', true, ?, ?)
                """, id, projectId, name, OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private UUID insertProposal(UUID projectId, UUID analysisId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into validatable_proposals
                    (id, project_id, analysis_id, type, status, payload, created_at)
                values (?, ?, ?, 'ENGINEERING_DECISION', 'ACCEPTED', '{}'::jsonb, ?)
                """, id, projectId, analysisId, OffsetDateTime.now());
        return id;
    }

    private UUID insertAnalysis(UUID projectId, UUID sourceId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into analyses
                    (id, project_id, selected_source_id, selected_source_snapshot,
                     understanding_execution_key, type, status, intent_id, intent_version,
                     created_at, updated_at)
                values (?, ?, ?, cast(? as jsonb), ?, 'ARCHITECTURE_REVIEW', 'PENDING',
                        'describe-project', 'v1', ?, ?)
                """, id, projectId, sourceId, "{\"id\":\"" + sourceId + "\"}", UUID.randomUUID(),
                OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private UUID insertValidation(UUID proposalId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into validations
                    (id, proposal_id, decision, validated_at, validated_by)
                values (?, ?, 'ACCEPTED', ?, '123e4567-e89b-42d3-a456-426614174000')
                """, id, proposalId, OffsetDateTime.now());
        return id;
    }

    private UUID insertEngineeringEvent(
            UUID projectId, UUID analysisId, UUID sourceId, UUID proposalId, UUID validationId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into engineering_events
                    (id, project_id, analysis_id, source_id, proposal_id, validation_id, category,
                     title, summary, significance, base_commit, target_commit, occurred_at,
                     created_at)
                values (?, ?, ?, ?, ?, ?, 'ARCHITECTURE_CHANGE', 'Event', 'Summary', 'Sig',
                        ?, ?, ?, ?)
                """, id, projectId, analysisId, sourceId, proposalId, validationId,
                "a".repeat(40), "b".repeat(40), OffsetDateTime.now(), OffsetDateTime.now());
        return id;
    }

    private void insertEngineeringStory(UUID projectId, int storyNumber) {
        jdbc.update("""
                insert into engineering_stories
                    (id, project_id, story_number, title, status, story_path, base_commit,
                     target_commit, created_at, updated_at)
                values (?, ?, ?, 'Story', 'REGISTERED', 'stories/story-1', ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, storyNumber,
                "a".repeat(40), "b".repeat(40),
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private UUID insertFact(UUID analysisId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into facts (id, analysis_id, type, content, source, detected_at)
                values (?, ?, 'SOURCE_DIRECTORY_PRESENT', 'Content', 'src/Service.java:1',
                        cast(? as timestamp with time zone))
                """, id, analysisId, OffsetDateTime.now());
        jdbc.update("""
                insert into fact_evidence_references (fact_id, reference) values (?, ?)
                """, id, "pom.xml:1");
        jdbc.update("""
                insert into fact_evidence_references (fact_id, reference) values (?, ?)
                """, id, "src/Service.java:2");
        return id;
    }

    private UUID insertObservation(UUID analysisId, UUID factId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into observations
                    (id, analysis_id, type, content, rule_id, rule_version, created_at)
                values (?, ?, 'SPRING_BOOT_REST_APPLICATION', 'Content', 'rule-a', 'v1',
                        cast(? as timestamp with time zone))
                """, id, analysisId, OffsetDateTime.now());
        jdbc.update("""
                insert into observation_facts (observation_id, fact_id) values (?, ?)
                """, id, factId);
        return id;
    }

    private void insertProfile(UUID projectId, UUID analysisId) {
        jdbc.update("""
                insert into project_profile_snapshots
                    (id, project_id, analysis_id, profile_version, renderer_version, generated_at,
                     requested_revision, resolved_revisions, completeness_status, collection_complete,
                     truncated, warning_count, error_count, successful_collector_count,
                     collectors_with_warnings_count, failed_collector_count, sections,
                     deterministic_summary, source_observations, characteristic_count)
                values (?, ?, ?, 'project-profile-v1', 'project-profile-renderer-v1', ?,
                        null, '{}'::jsonb, 'PARTIAL', true, false, 0, 0, 1, 0, 0,
                        '[]'::jsonb, '', '[]'::jsonb, 0)
                """, UUID.randomUUID(), projectId, analysisId, OffsetDateTime.now());
    }
}