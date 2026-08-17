package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.mapper.EngineeringContextContractMapper;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineeringContextContractMapperTest {
    private final ProjectContextContractMapper projectContextContractMapper =
            mock(ProjectContextContractMapper.class);

    private final EngineeringContextContractMapper mapper =
            new EngineeringContextContractMapper(projectContextContractMapper);

    @Test
    void shouldMapEngineeringContextWithEvidenceAndSelectionReason() {
        ProjectContextSnapshot projectSnapshot = mock(ProjectContextSnapshot.class);

        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class
        );

        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        RepositoryEvidence.EvidenceProvenance provenance =
                mock(RepositoryEvidence.EvidenceProvenance.class);

        when(evidence.reference()).thenReturn("evidence-1");
        when(evidence.kind()).thenReturn("CHANGED_FILE");
        when(evidence.layer()).thenReturn(
                com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer.COMMIT_DIFF
        );
        when(evidence.summary()).thenReturn("Project note rendering changed");
        when(evidence.provenance()).thenReturn(provenance);
        when(evidence.relevanceScore()).thenReturn(92);

        when(provenance.sourceType()).thenReturn("GIT");
        when(provenance.originatingFile())
                .thenReturn("frontend/project-context-inputs-section.html");
        when(provenance.identifier()).thenReturn("abc123");

        RepositoryContext.SelectionDecision selectionDecision =
                new RepositoryContext.SelectionDecision(
                        "evidence-1",
                        true,
                        "SELECTED_BY_RANK",
                        92,
                        120
                );

        RepositoryContext repositoryContext = mock(RepositoryContext.class);

        when(repositoryContext.evidence()).thenReturn(List.of(evidence));
        when(repositoryContext.selectionDecisions())
                .thenReturn(List.of(selectionDecision));
        when(repositoryContext.candidateCount()).thenReturn(4);
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(120);
        when(repositoryContext.contextDigest()).thenReturn("digest-123");

        String intent =
                "Investigate why Project Notes Markdown is displayed incorrectly.";

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, intent);

        assertThat(result.project()).isSameAs(mappedProject);
        assertThat(result.intent()).isEqualTo(intent);

        assertThat(result.evidence()).hasSize(1);

        var mappedEvidence = result.evidence().getFirst();

        assertThat(mappedEvidence.kind()).isEqualTo("CHANGED_FILE");
        assertThat(mappedEvidence.layer()).isEqualTo("COMMIT_DIFF");
        assertThat(mappedEvidence.summary())
                .isEqualTo("Project note rendering changed");

        assertThat(mappedEvidence.sourceType()).isEqualTo("GIT");
        assertThat(mappedEvidence.originatingFile())
                .isEqualTo("frontend/project-context-inputs-section.html");
        assertThat(mappedEvidence.identifier()).isEqualTo("abc123");

        assertThat(mappedEvidence.relevanceScore()).isEqualTo(92);
        assertThat(mappedEvidence.selectionReason())
                .isEqualTo("SELECTED_BY_RANK");

        assertThat(result.metadata().candidateCount()).isEqualTo(4);
        assertThat(result.metadata().selectedCount()).isEqualTo(1);
        assertThat(result.metadata().truncated()).isFalse();
        assertThat(result.metadata().usedTokens()).isEqualTo(120);
        assertThat(result.metadata().contextDigest())
                .isEqualTo("digest-123");
    }
}
