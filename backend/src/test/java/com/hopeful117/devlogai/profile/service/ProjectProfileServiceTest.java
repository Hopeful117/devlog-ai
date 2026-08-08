package com.hopeful117.devlogai.profile.service;

import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectProfileServiceTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private AnalysisExecutionDiagnosticRepository diagnosticRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private ProjectProfileSnapshotRepository profileRepository;
    @Mock private ProjectProfileRenderer renderer;

    private ProjectProfileServiceImpl createService() {
        return new ProjectProfileServiceImpl(
                analysisRepository, diagnosticRepository, observationRepository,
                profileRepository, renderer
        );
    }

    @Test
    void shouldReturnExistingProfileWhenAlreadyBuilt() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();
        ProjectProfileSnapshot existing = ProjectProfileSnapshot.builder()
                .id(UUID.randomUUID()).project(project).analysis(analysis)
                .profileVersion("project-profile-v1").rendererVersion("test")
                .generatedAt(Instant.now()).requestedRevision("main")
                .resolvedRevisions(Map.of())
                .completenessStatus(ProfileCompletenessStatus.COMPLETE)
                .collectionComplete(true).truncated(false)
                .warningCount(0).errorCount(0)
                .successfulCollectorCount(3).collectorsWithWarningsCount(0).failedCollectorCount(0)
                .sections(List.of()).deterministicSummary("summary")
                .sourceObservations(List.of()).characteristicCount(0)
                .build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.of(existing));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        assertEquals(projectId, result.projectId());
        assertEquals(analysisId, result.analysisId());
        verify(analysisRepository, never()).findById(any());
        verify(diagnosticRepository, never()).findById(any());
    }

    @Test
    void shouldThrowWhenAnalysisNotFound() {
        UUID analysisId = UUID.randomUUID();
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        ProjectProfileServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.build(analysisId));
    }

    @Test
    void shouldThrowWhenDiagnosticsNotFound() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.empty());

        ProjectProfileServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.build(analysisId));
    }

    @Test
    void shouldBuildProfileFromAnalysisWithObservations() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();

        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder()
                .id(analysisId).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW).status(AnalysisStatus.IN_PROGRESS)
                .targetRevision("main").build();

        Fact fact = Fact.builder()
                .id(factId).analysis(analysis).type(FactType.SPRING_BOOT_DETECTED)
                .content("Spring Boot 3.2").source("spring-collector-v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("pom.xml")))
                .fingerprint("fp1").build();

        Observation observation = Observation.builder()
                .id(observationId).analysis(analysis)
                .type(ObservationType.SPRING_BOOT_REST_APPLICATION)
                .content("Spring Boot REST app detected")
                .ruleId("SPRING_BOOT_RULE").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(1).observationCount(1)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of(projectId.toString(), "abc"))
                .collectorVersions(Map.of("SPRING", "v1"))
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));
        when(renderer.render(any(), any())).thenReturn("Spring Boot detected");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        assertEquals(projectId, result.projectId());
        assertEquals(analysisId, result.analysisId());
        assertEquals(ProfileCompletenessStatus.COMPLETE, result.completeness().status());
        assertTrue(result.characteristicCount() > 0);

        verify(profileRepository).save(any());
    }

    @Test
    void shouldMarkProfileAsPartialWhenWarningsPresent() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder()
                .id(analysisId).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW).status(AnalysisStatus.IN_PROGRESS)
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(0).observationCount(0)
                .warningCount(1).errorCount(0)
                .collectorCount(1).successfulCollectors(0)
                .collectorsWithWarnings(1).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of());
        when(renderer.render(any(), any())).thenReturn("No characteristics");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertEquals(ProfileCompletenessStatus.PARTIAL, result.completeness().status());
        assertEquals(1, result.completeness().warningCount());
    }

    @Test
    void shouldThrowWhenObservationAnalysisMismatch() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID otherAnalysisId = UUID.randomUUID();

        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();
        Analysis otherAnalysis = Analysis.builder().id(otherAnalysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(UUID.randomUUID()).analysis(otherAnalysis)
                .type(FactType.OTHER).content("test").source("v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("f.txt")))
                .fingerprint("fp").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.SPRING_BOOT_REST_APPLICATION)
                .content("test").ruleId("R1").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));

        ProjectProfileServiceImpl service = createService();
        assertThrows(IllegalArgumentException.class, () -> service.build(analysisId));
    }

    @Test
    void shouldThrowWhenObservationHasNoSupportingFacts() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.SPRING_BOOT_REST_APPLICATION)
                .content("test").ruleId("R1").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>())
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));

        ProjectProfileServiceImpl service = createService();
        assertThrows(IllegalArgumentException.class, () -> service.build(analysisId));
    }

    @Test
    void shouldThrowWhenObservationRuleTraceabilityMissing() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(FactType.OTHER).content("test").source("v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("f.txt")))
                .fingerprint("fp").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.SPRING_BOOT_REST_APPLICATION)
                .content("test").ruleId(null).ruleVersion(null)
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));

        ProjectProfileServiceImpl service = createService();
        assertThrows(IllegalArgumentException.class, () -> service.build(analysisId));
    }

    @Test
    void shouldGetByAnalysisExisting() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();
        ProjectProfileSnapshot snapshot = ProjectProfileSnapshot.builder()
                .id(UUID.randomUUID()).project(project).analysis(analysis)
                .profileVersion("v1").rendererVersion("r1")
                .generatedAt(Instant.now())
                .resolvedRevisions(Map.of())
                .completenessStatus(ProfileCompletenessStatus.COMPLETE)
                .collectionComplete(true).truncated(false)
                .warningCount(0).errorCount(0)
                .successfulCollectorCount(1).collectorsWithWarningsCount(0).failedCollectorCount(0)
                .sections(List.of()).deterministicSummary("summary")
                .sourceObservations(List.of()).characteristicCount(0)
                .build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.of(snapshot));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.getByAnalysis(analysisId);

        assertNotNull(result);
        assertEquals(analysisId, result.analysisId());
    }

    @Test
    void shouldThrowWhenGetByAnalysisNotFound() {
        UUID analysisId = UUID.randomUUID();
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());

        ProjectProfileServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getByAnalysis(analysisId));
    }

    @Test
    void shouldGetLatestByProject() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();
        ProjectProfileSnapshot snapshot = ProjectProfileSnapshot.builder()
                .id(UUID.randomUUID()).project(project).analysis(analysis)
                .profileVersion("v1").rendererVersion("r1")
                .generatedAt(Instant.now())
                .resolvedRevisions(Map.of())
                .completenessStatus(ProfileCompletenessStatus.PARTIAL)
                .collectionComplete(false).truncated(true)
                .warningCount(2).errorCount(1)
                .successfulCollectorCount(1).collectorsWithWarningsCount(1).failedCollectorCount(1)
                .sections(List.of()).deterministicSummary("summary")
                .sourceObservations(List.of()).characteristicCount(0)
                .build();

        when(profileRepository.findFirstByProjectIdOrderByGeneratedAtDescIdDesc(projectId))
                .thenReturn(Optional.of(snapshot));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.getLatestByProject(projectId);

        assertNotNull(result);
        assertEquals(projectId, result.projectId());
        assertEquals(ProfileCompletenessStatus.PARTIAL, result.completeness().status());
    }

    @Test
    void shouldThrowWhenGetLatestByProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(profileRepository.findFirstByProjectIdOrderByGeneratedAtDescIdDesc(projectId))
                .thenReturn(Optional.empty());

        ProjectProfileServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getLatestByProject(projectId));
    }

    @Test
    void shouldMarkProfileAsPartialWhenCollectionIncomplete() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(0).observationCount(0)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(0)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(false).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of());
        when(renderer.render(any(), any())).thenReturn("Partial");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertEquals(ProfileCompletenessStatus.PARTIAL, result.completeness().status());
    }

    @Test
    void shouldMarkProfileAsPartialWhenTruncated() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(0).observationCount(0)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(true)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of());
        when(renderer.render(any(), any())).thenReturn("Truncated");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertEquals(ProfileCompletenessStatus.PARTIAL, result.completeness().status());
    }

    @Test
    void shouldMarkProfileAsPartialWhenFailedCollectors() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(0).observationCount(0)
                .warningCount(0).errorCount(0)
                .collectorCount(2).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(1)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of());
        when(renderer.render(any(), any())).thenReturn("Partial");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertEquals(ProfileCompletenessStatus.PARTIAL, result.completeness().status());
    }

    @Test
    void shouldHandleContainerizedProjectObservation() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(factId).analysis(analysis).type(FactType.DOCKERFILE_PRESENT)
                .content("Dockerfile found").source("docker-v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("Dockerfile")))
                .fingerprint("fp-docker").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.CONTAINERIZED_PROJECT)
                .content("Docker configured").ruleId("DOCKER_RULE").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(1).observationCount(1)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));
        when(renderer.render(any(), any())).thenReturn("Docker");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        assertEquals(1, result.characteristicCount());
    }

    @Test
    void shouldHandleArchitectureDocumentationObservation() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(factId).analysis(analysis).type(FactType.ADR_DOCUMENT_PRESENT)
                .content("ADR found").source("doc-v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("docs/adr/001.md")))
                .fingerprint("fp-adr").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.ARCHITECTURE_DOCUMENTATION_PRESENT)
                .content("ADR present").ruleId("ADR_RULE").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(1).observationCount(1)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));
        when(renderer.render(any(), any())).thenReturn("ADR");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        assertEquals(1, result.characteristicCount());
    }

    @Test
    void shouldHandleAutomatedTestSuiteObservation() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(factId).analysis(analysis).type(FactType.TEST_FILE_PRESENT)
                .content("Tests found").source("test-v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("src/test")))
                .fingerprint("fp-test").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.AUTOMATED_TEST_SUITE_PRESENT)
                .content("Test suite present").ruleId("TEST_RULE").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(1).observationCount(1)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));
        when(renderer.render(any(), any())).thenReturn("Tests");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        assertEquals(1, result.characteristicCount());
    }

    @Test
    void shouldHandleIntegrationTestSuiteObservation() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(factId).analysis(analysis).type(FactType.INTEGRATION_TEST_FILE_PRESENT)
                .content("Integration tests found").source("test-v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("src/test/integration")))
                .fingerprint("fp-it").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.INTEGRATION_TEST_SUITE_PRESENT)
                .content("Integration tests present").ruleId("IT_RULE").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(1).observationCount(1)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));
        when(renderer.render(any(), any())).thenReturn("Integration tests");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        assertEquals(1, result.characteristicCount());
    }

    @Test
    void shouldHandleMultiModuleBuildObservation() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();

        Fact fact = Fact.builder()
                .id(factId).analysis(analysis).type(FactType.MULTI_MODULE_STRUCTURE_PRESENT)
                .content("Multi-module found").source("build-v1")
                .evidenceReferences(new LinkedHashSet<>(List.of("pom.xml")))
                .fingerprint("fp-multi").build();

        Observation observation = Observation.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .type(ObservationType.MULTI_MODULE_BUILD)
                .content("Multi-module build").ruleId("BUILD_RULE").ruleVersion("1")
                .supportingFacts(new LinkedHashSet<>(Set.of(fact)))
                .build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(1).observationCount(1)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(observationRepository.findByAnalysisIdOrderByTypeAscIdAsc(analysisId))
                .thenReturn(List.of(observation));
        when(renderer.render(any(), any())).thenReturn("Multi-module");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectProfileServiceImpl service = createService();
        ProjectProfileResponse result = service.build(analysisId);

        assertNotNull(result);
        // MULTI_MODULE_BUILD maps to both BUILD and ARCHITECTURE categories
        assertEquals(2, result.characteristicCount());
    }
}
