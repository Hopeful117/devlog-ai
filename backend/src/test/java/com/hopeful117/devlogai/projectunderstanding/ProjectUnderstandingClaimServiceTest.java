package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ProjectUnderstandingClaimServiceTest {

    private static final String RESOLVED = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Mock private AnalysisRepository analysisRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SourceRepository sourceRepository;

    private ProjectUnderstandingClaimService claimService;
    private IntentDefinition intent;
    private UUID projectId;
    private UUID sourceId;
    private Project project;
    private Source source;

    @BeforeEach
    void setUp() {
        claimService = new ProjectUnderstandingClaimService(
                analysisRepository, projectRepository, sourceRepository,
                new ProjectUnderstandingExecutionKey(new ObjectMapper()), new ObjectMapper());
        intent = new IntentDefinition("describe-project", "v1", "describe",
                List.of(), List.of(), Map.of(), "prompt");
        projectId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        project = Project.builder().id(projectId).build();
        source = Source.builder().id(sourceId).project(project).active(true).build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(sourceRepository.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(analysisRepository.findByUnderstandingExecutionKeyAndStatusIn(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void persistsResolvedRevisionWhenNoRevisionRequested() {
        PreparedProjectUnderstanding prepared = prepared(null, RESOLVED);

        claimService.claim(prepared);

        Analysis persisted = persistedAnalysis();
        assertThat(persisted.getTargetRevision()).isEqualTo(RESOLVED);
        assertThat(persisted.getSelectedSource()).isEqualTo(source);
    }

    @Test
    void persistsResolvedRevisionWhenFullShaRequested() {
        PreparedProjectUnderstanding prepared = prepared(RESOLVED, RESOLVED);

        claimService.claim(prepared);

        assertThat(persistedAnalysis().getTargetRevision()).isEqualTo(RESOLVED);
    }

    @Test
    void persistsResolvedRevisionWhenSymbolicRevisionRequested() {
        PreparedProjectUnderstanding prepared = prepared("main", RESOLVED);

        claimService.claim(prepared);

        assertThat(persistedAnalysis().getTargetRevision()).isEqualTo(RESOLVED);
    }

    @Test
    void persistsResolvedRevisionWhenShortShaRequested() {
        PreparedProjectUnderstanding prepared = prepared(RESOLVED.substring(0, 12), RESOLVED);

        claimService.claim(prepared);

        assertThat(persistedAnalysis().getTargetRevision()).isEqualTo(RESOLVED);
    }

    @Test
    void persistsResolvedRevisionWhenRequestedDiffersFromObserved() {
        PreparedProjectUnderstanding prepared = prepared("refs/heads/main", RESOLVED);

        claimService.claim(prepared);

        assertThat(persistedAnalysis().getTargetRevision()).isEqualTo(RESOLVED);
    }

    private PreparedProjectUnderstanding prepared(String requested, String resolved) {
        return new PreparedProjectUnderstanding(projectId, sourceId, requested, resolved,
                null, intent, Map.of("id", sourceId.toString(), "name", "Core"));
    }

    private Analysis persistedAnalysis() {
        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);
        verify(analysisRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
