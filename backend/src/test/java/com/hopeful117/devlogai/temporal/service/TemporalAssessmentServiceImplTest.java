package com.hopeful117.devlogai.temporal.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.temporal.domain.TemporalAssessment;
import com.hopeful117.devlogai.temporal.port.RepositoryStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporalAssessmentServiceImplTest {

    @Mock
    private RepositoryStatePort repositoryStatePort;
    @Mock
    private ProjectCommitRepository projectCommitRepository;

    @InjectMocks
    private TemporalAssessmentServiceImpl service;

    private UUID projectId;
    private UUID sourceId;
    private Insight activeInsight;
    private Analysis analysis;
    private Source source;
    private Project project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sourceId = UUID.randomUUID();

        project = new Project();
        project.setId(projectId);

        source = new Source();
        source.setId(sourceId);
        source.setActive(true);
        source.setType(SourceType.GIT_REPOSITORY);

        analysis = new Analysis();
        analysis.setId(UUID.randomUUID());
        analysis.setProject(project);
        analysis.setSelectedSource(source);
        analysis.setTargetRevision("abc123");

        activeInsight = new Insight();
        activeInsight.setId(UUID.randomUUID());
        activeInsight.setProject(project);
        activeInsight.setAnalysis(analysis);
        activeInsight.setStatus(InsightStatus.ACTIVE);
        activeInsight.setEvidenceReferences(new ArrayList<>());
    }

    @Test
    void shouldReturnUnknown_When_EvidenceReferences_Empty() {
        activeInsight.setEvidenceReferences(List.of());

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.UNKNOWN, result.getConclusion());
        assertTrue(result.getSupportingEvidence().get(0).contains("no evidence references"));
    }

    @Test
    void shouldReturnUnknown_When_SelectedSource_Missing() {
        analysis.setSelectedSource(null);
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.UNKNOWN, result.getConclusion());
        assertTrue(result.getSupportingEvidence().get(0).contains("selectedSource unavailable"));
    }

    @Test
    void shouldReturnUnknown_When_TargetRevision_Missing() {
        analysis.setTargetRevision(null);
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.UNKNOWN, result.getConclusion());
        assertTrue(result.getSupportingEvidence().get(0).contains("baseline revision"));
    }

    @Test
    void shouldReturnUnknown_When_CurrentKnownRevision_Missing() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.empty());

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.UNKNOWN, result.getConclusion());
        assertTrue(result.getSupportingEvidence().get(0).contains("currentKnownRevision"));
    }

    @Test
    void shouldReturnUnknown_When_RepositoryStatePort_Fails() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenThrow(new GitCommandException("Workspace unavailable"));

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.UNKNOWN, result.getConclusion());
        assertTrue(result.getSupportingEvidence().get(0).contains("repository state verification unavailable"));
    }

    @Test
    void shouldReturnCurrent_When_PositivelyVerified() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "def456", "src/main/java/Foo.java"))
                .thenReturn(true);

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.CURRENT, result.getConclusion());
        assertEquals(TemporalAssessment.ReasoningOrigin.DETERMINISTIC, result.getReasoningOrigin());
        assertTrue(result.getSupportingEvidence().stream()
                .anyMatch(s -> s.contains("verified present")));
    }

    @Test
    void shouldReturnCurrent_When_NonEvaluableRefs_Skipped() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java", "src/main/java/Gone.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "def456", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Gone.java"))
                .thenReturn(false);

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.CURRENT, result.getConclusion());
        assertTrue(result.getSupportingEvidence().stream()
                .anyMatch(s -> s.contains("verified present")));
        assertTrue(result.getSupportingEvidence().stream()
                .anyMatch(s -> s.contains("does not prove temporal degradation")));
    }

    @Test
    void shouldReturnSuspectedStale_When_BaselineToCurrent_Transition() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "def456", "src/main/java/Foo.java"))
                .thenReturn(false);

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.SUSPECTED_STALE, result.getConclusion());
        assertTrue(result.getSupportingEvidence().stream()
                .anyMatch(s -> s.contains("present at baseline") && s.contains("absent at currentKnownRevision")));
    }

    @Test
    void shouldReturnSuspectedStale_With_Enrichment_When_DeletedFileFound() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        activeInsight.setCreatedAt(Instant.parse("2024-01-15T10:00:00Z"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(eq(projectId), any(Instant.class)))
                .thenReturn(List.of(createCommitWithDeletedFile("src/main/java/Foo.java")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "def456", "src/main/java/Foo.java"))
                .thenReturn(false);

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.SUSPECTED_STALE, result.getConclusion());
        assertTrue(result.getSupportingEvidence().stream()
                .anyMatch(s -> s.contains("Corroborating: file")));
    }

    @Test
    void shouldReturnUnknown_When_AllRefs_NonEvaluable() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/NeverExisted.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/NeverExisted.java"))
                .thenReturn(false);

        TemporalAssessment result = service.assess(activeInsight);

        assertEquals(TemporalAssessment.Conclusion.UNKNOWN, result.getConclusion());
        assertTrue(result.getSupportingEvidence().get(0).contains("no evidence references evaluable"));
    }

    @Test
    void shouldReject_NonActiveInsight() {
        activeInsight.setStatus(InsightStatus.SUPERSEDED);
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));

        assertThrows(IllegalStateException.class, () -> service.assess(activeInsight));
    }

    @Test
    void shouldReturnRepeatable_Same_State_Same_Conclusion() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "def456", "src/main/java/Foo.java"))
                .thenReturn(true);

        TemporalAssessment result1 = service.assess(activeInsight);
        TemporalAssessment result2 = service.assess(activeInsight);

        assertEquals(result1.getConclusion(), result2.getConclusion());
        assertEquals(result1.getSupportingEvidence(), result2.getSupportingEvidence());
    }

    @Test
    void shouldUse_CorrectSource_For_Port_Calls() {
        activeInsight.setEvidenceReferences(List.of("src/main/java/Foo.java"));
        when(projectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId))
                .thenReturn(Optional.of(createCommit("def456")));
        when(repositoryStatePort.isFilePresentAtRevision(source, "abc123", "src/main/java/Foo.java"))
                .thenReturn(true);
        when(repositoryStatePort.isFilePresentAtRevision(source, "def456", "src/main/java/Foo.java"))
                .thenReturn(true);

        service.assess(activeInsight);

        verify(repositoryStatePort, times(2)).isFilePresentAtRevision(eq(source), anyString(), anyString());
        verify(projectCommitRepository).findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId);
    }

    private ProjectCommit createCommit(String commitHash) {
        var commit = new ProjectCommit();
        commit.setId(UUID.randomUUID());
        commit.setProject(project);
        commit.setSource(source);
        commit.setCommitHash(commitHash);
        commit.setCommittedAt(Instant.now());
        commit.setChangedFiles(new java.util.ArrayList<>());
        return commit;
    }

    private ProjectCommit createCommitWithDeletedFile(String deletedPath) {
        var commit = createCommit("xyz789");
        var deletedFile = new ChangedFile();
        deletedFile.setChangeType(FileChangeType.DELETED);
        deletedFile.setOldPath(deletedPath);
        deletedFile.setNewPath(null);
        deletedFile.setCommit(commit);
        commit.setChangedFiles(new java.util.ArrayList<>(List.of(deletedFile)));
        return commit;
    }
}
