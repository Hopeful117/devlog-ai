package com.hopeful117.devlogai.proposal.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.dto.request.CreateValidatableProposalRequest;
import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.mapper.ValidatableProposalMapper;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidatableProposalServiceAdditionalTest {

    @Mock private ValidatableProposalRepository proposalRepository;
    @Mock private ValidatableProposalMapper proposalMapper;
    @Mock private ProjectRepository projectRepository;
    @Mock private AnalysisRepository analysisRepository;
    @InjectMocks private ValidatableProposalServiceImpl service;

    private CreateValidatableProposalRequest createRequest(UUID projectId, UUID analysisId) {
        return new CreateValidatableProposalRequest(projectId, analysisId, ProposalType.INSIGHT, Map.of());
    }

    private ValidatableProposalResponse createResponse(UUID id, UUID projectId, UUID analysisId) {
        return new ValidatableProposalResponse(id, projectId, analysisId, ProposalType.INSIGHT,
                ProposalStatus.PROPOSED, Map.of(), Instant.now(), null);
    }

    @Test
    void shouldCreateProposal() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).name("Test Project").build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW).status(AnalysisStatus.COMPLETED).build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(proposalMapper.toEntity(any())).thenReturn(ValidatableProposal.builder().build());
        when(proposalMapper.toResponse(any())).thenReturn(createResponse(UUID.randomUUID(), projectId, analysisId));

        ValidatableProposalResponse result = service.create(createRequest(projectId, analysisId));

        assertNotNull(result);
        verify(proposalRepository).save(any());
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        CreateValidatableProposalRequest request = createRequest(
                projectId, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request));
    }

    @Test
    void shouldThrowWhenAnalysisNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(Project.builder().id(projectId).build()));
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());
        CreateValidatableProposalRequest request = createRequest(projectId, analysisId);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request));
    }

    @Test
    void shouldThrowWhenAnalysisBelongsToDifferentProject() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId)
                .project(Project.builder().id(otherProjectId).build()).build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        CreateValidatableProposalRequest request = createRequest(projectId, analysisId);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request));
    }

    @Test
    void shouldGetById() {
        UUID id = UUID.randomUUID();
        ValidatableProposal proposal = ValidatableProposal.builder().id(id).build();
        when(proposalRepository.findById(id)).thenReturn(Optional.of(proposal));
        when(proposalMapper.toResponse(proposal)).thenReturn(createResponse(id, UUID.randomUUID(), UUID.randomUUID()));

        ValidatableProposalResponse result = service.getById(id);
        assertNotNull(result);
        assertEquals(id, result.id());
    }

    @Test
    void shouldThrowWhenGetByIdNotFound() {
        when(proposalRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        UUID proposalId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.getById(proposalId));
    }

    @Test
    void shouldGetByProjectIdAndStatus() {
        UUID projectId = UUID.randomUUID();
        when(proposalRepository.findByProjectIdAndStatus(projectId, ProposalStatus.PROPOSED)).thenReturn(List.of());
        assertTrue(service.getByProjectIdAndStatus(projectId).isEmpty());
    }

    @Test
    void shouldGetByProjectId() {
        UUID projectId = UUID.randomUUID();
        when(proposalRepository.findByProjectId(projectId)).thenReturn(List.of());
        assertTrue(service.getByProjectId(projectId).isEmpty());
    }

    @Test
    void shouldGetByAnalysisId() {
        UUID analysisId = UUID.randomUUID();
        when(proposalRepository.findByAnalysisId(analysisId)).thenReturn(List.of());
        assertTrue(service.getByAnalysisId(analysisId).isEmpty());
    }
}
