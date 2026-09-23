package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.challenge.repository.ChallengeRepository;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersistedFamilyEvidenceResolverTest {
    private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROJECT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ANALYSIS_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void resolvesDecisionAndPreservesProjectOwnership() {
        DecisionRepository repository = mock(DecisionRepository.class);
        Decision decision = mock(Decision.class);
        when(decision.getId()).thenReturn(ID);
        when(decision.getProject()).thenReturn(project());
        when(decision.getTitle()).thenReturn("Use Core facade");
        when(repository.findDetailedById(ID)).thenReturn(Optional.of(decision));

        EvidenceResolutionResult result = resolve(
                new DecisionEvidenceResolver(repository), "decision:" + ID);

        assertThat(result.metadata().provenance()).isEqualTo("decision");
        assertThat(((DecisionResolutionPayload) result.payload()).projectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void resolvesEngineeringEventThroughItsDetailedRepositoryAuthority() {
        EngineeringEventRepository repository = mock(EngineeringEventRepository.class);
        EngineeringEvent event = mock(EngineeringEvent.class);
        when(event.getId()).thenReturn(ID);
        when(event.getProject()).thenReturn(project());
        when(event.getAnalysis()).thenReturn(Analysis.builder().id(ANALYSIS_ID).build());
        when(event.getProposal()).thenReturn(mock(com.hopeful117.devlogai.proposal.entity.ValidatableProposal.class));
        when(event.getValidation()).thenReturn(mock(com.hopeful117.devlogai.validation.entity.Validation.class));
        when(event.getSource()).thenReturn(mock(com.hopeful117.devlogai.source.entity.Source.class));
        when(repository.findDetailedById(ID)).thenReturn(Optional.of(event));

        EvidenceResolutionResult result = resolve(
                new EngineeringEventEvidenceResolver(repository), "event:" + ID);

        assertThat(((EngineeringEventResolutionPayload) result.payload()).analysisId())
                .isEqualTo(ANALYSIS_ID);
    }

    @Test
    void resolvesStory() {
        EngineeringStoryRepository repository = mock(EngineeringStoryRepository.class);
        EngineeringStory story = mock(EngineeringStory.class);
        when(story.getId()).thenReturn(ID);
        when(story.getProject()).thenReturn(project());
        when(story.getTitle()).thenReturn("Story");
        when(repository.findDetailedById(ID)).thenReturn(Optional.of(story));

        EvidenceResolutionResult result = resolve(new StoryEvidenceResolver(repository), "story:" + ID);

        assertThat(((StoryResolutionPayload) result.payload()).title()).isEqualTo("Story");
    }

    @Test
    void resolvesAnalysisWithSelectedSourceAndSnapshotMetadata() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        Analysis analysis = mock(Analysis.class);
        when(analysis.getId()).thenReturn(ID);
        when(analysis.getProject()).thenReturn(project());
        when(analysis.getUserGuidance()).thenReturn(Map.of("audience", "engineers"));
        when(analysis.getSelectedSourceSnapshot()).thenReturn(Map.of("revision", "abc"));
        when(repository.findWithProjectById(ID)).thenReturn(Optional.of(analysis));

        EvidenceResolutionResult result = resolve(
                new AnalysisEvidenceResolver(repository), "analysis:" + ID);

        AnalysisResolutionPayload payload = (AnalysisResolutionPayload) result.payload();
        assertThat(payload.projectId()).isEqualTo(PROJECT_ID);
        assertThat(payload.userGuidance()).containsEntry("audience", "engineers");
    }

    @Test
    void resolvesArtifact() {
        ArtifactRepository repository = mock(ArtifactRepository.class);
        Artifact artifact = mock(Artifact.class);
        when(artifact.getId()).thenReturn(ID);
        when(artifact.getProject()).thenReturn(project());
        when(artifact.getName()).thenReturn("ADR");
        when(repository.findDetailedById(ID)).thenReturn(Optional.of(artifact));

        EvidenceResolutionResult result = resolve(
                new ArtifactEvidenceResolver(repository), "artifact:" + ID);

        assertThat(((ArtifactResolutionPayload) result.payload()).name()).isEqualTo("ADR");
    }

    @Test
    void resolvesChallenge() {
        ChallengeRepository repository = mock(ChallengeRepository.class);
        Challenge challenge = mock(Challenge.class);
        when(challenge.getId()).thenReturn(ID);
        when(challenge.getProject()).thenReturn(project());
        when(challenge.getTitle()).thenReturn("Open question");
        when(repository.findDetailedById(ID)).thenReturn(Optional.of(challenge));

        EvidenceResolutionResult result = resolve(
                new ChallengeEvidenceResolver(repository), "challenge:" + ID);

        assertThat(((ChallengeResolutionPayload) result.payload()).title()).isEqualTo("Open question");
    }

    @Test
    void rejectsTaskSnapshotForEveryCurrentStateFamilyBeforeRepositoryAccess() {
        DecisionRepository repository = mock(DecisionRepository.class);
        EvidenceFamilyResolver resolver = new DecisionEvidenceResolver(repository);

        assertThatThrownBy(() -> new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(), List.of(resolver)).resolve(
                        new EvidenceResolutionRequest("decision:" + ID,
                                EvidenceResolutionMode.TASK_SNAPSHOT)))
                .isInstanceOf(EvidenceResolutionException.class)
                .extracting("code")
                .isEqualTo(EvidenceResolutionFailureCode.UNAUTHORIZED);
        verifyNoInteractions(repository);
    }

    private EvidenceResolutionResult resolve(EvidenceFamilyResolver resolver, String reference) {
        return new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(), List.of(resolver)).resolve(
                        new EvidenceResolutionRequest(reference, EvidenceResolutionMode.CURRENT));
    }

    private Project project() {
        return Project.builder().id(PROJECT_ID).build();
    }
}
