package com.hopeful117.devlogai.proposal.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.dto.request.CreateValidatableProposalRequest;
import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.mapper.ValidatableProposalMapper;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ValidatableProposalServiceTest {
    @Mock
    private ValidatableProposalRepository proposalRepository;

    @Mock
    private ValidatableProposalMapper proposalMapper;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @InjectMocks
    private ValidatableProposalServiceImpl service;

    @Test
    void shouldCreateProposal() {

        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        Project project = Project.builder()
                .id(projectId)
                .build();

        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .project(project)
                .build();

        CreateValidatableProposalRequest request =
                new CreateValidatableProposalRequest(
                        projectId,
                        analysisId,
                        ProposalType.INSIGHT,
                        Map.of("summary", "proposal")
                );

        ValidatableProposal proposal =
                ValidatableProposal.builder()
                        .build();

        ValidatableProposal saved =
                ValidatableProposal.builder()
                        .id(UUID.randomUUID())
                        .project(project)
                        .analysis(analysis)
                        .build();

        ValidatableProposalResponse response =
                mock(ValidatableProposalResponse.class);

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(analysisRepository.findById(analysisId))
                .thenReturn(Optional.of(analysis));

        when(proposalMapper.toEntity(request))
                .thenReturn(proposal);

        when(proposalRepository.save(proposal))
                .thenReturn(saved);

        when(proposalMapper.toResponse(saved))
                .thenReturn(response);

        ValidatableProposalResponse result =
                service.create(request);

        assertThat(result).isSameAs(response);

        verify(proposalRepository).save(proposal);

        assertThat(proposal.getProject())
                .isSameAs(project);

        assertThat(proposal.getAnalysis())
                .isSameAs(analysis);
    }

    @Test
    void shouldRejectWhenProjectDoesNotExist() {

        UUID projectId = UUID.randomUUID();

        CreateValidatableProposalRequest request =
                mock(CreateValidatableProposalRequest.class);

        when(request.projectId())
                .thenReturn(projectId);

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");

        verifyNoInteractions(analysisRepository);
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void shouldRejectWhenAnalysisDoesNotExist() {

        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        Project project = Project.builder()
                .id(projectId)
                .build();

        CreateValidatableProposalRequest request =
                mock(CreateValidatableProposalRequest.class);

        when(request.projectId())
                .thenReturn(projectId);

        when(request.analysisId())
                .thenReturn(analysisId);

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(analysisRepository.findById(analysisId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.create(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Analysis not found");

        verifyNoInteractions(proposalRepository);
    }

    @Test
    void shouldRejectWhenAnalysisBelongsToAnotherProject() {

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .build();

        Project otherProject = Project.builder()
                .id(UUID.randomUUID())
                .build();

        Analysis analysis = Analysis.builder()
                .id(UUID.randomUUID())
                .project(otherProject)
                .build();

        CreateValidatableProposalRequest request =
                mock(CreateValidatableProposalRequest.class);

        when(request.projectId())
                .thenReturn(project.getId());

        when(request.analysisId())
                .thenReturn(analysis.getId());

        when(projectRepository.findById(project.getId()))
                .thenReturn(Optional.of(project));

        when(analysisRepository.findById(analysis.getId()))
                .thenReturn(Optional.of(analysis));

        assertThatThrownBy(() ->
                service.create(request)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Analysis does not belong to the specified project"
                );

        verifyNoInteractions(proposalRepository);
    }
}
