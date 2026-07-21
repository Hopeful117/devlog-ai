package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.insight.dto.request.CreateInsightRequest;
import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.mapper.InsightMapper;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InsightServiceTest {

    @Mock
    AnalysisRepository analysisRepository;

    @Mock
    InsightRepository insightRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    InsightMapper insightMapper;

    @InjectMocks
    InsightServiceImpl insightService;

    @Test
    void shouldCreateInsightSuccessfully() {

        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        CreateInsightRequest request =
                new CreateInsightRequest();

        request.setProjectId(projectId);
        request.setAnalysisId(analysisId);
        request.setType(InsightType.ARCHITECTURAL);
        request.setSeverity(InsightSeverity.WARNING);
        request.setTitle("Architecture issue");
        request.setContent("The current architecture may become difficult to maintain.");


        Project project = new Project();
        project.setId(projectId);

        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setProject(project);

        Insight insight = new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(analysisRepository.findById(analysisId))
                .thenReturn(Optional.of(analysis));

        when(insightMapper.toEntity(request))
                .thenReturn(insight);

        when(insightRepository.save(insight))
                .thenReturn(insight);

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        InsightResponse result =
                insightService.create(request);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);

        assertEquals(project, insight.getProject());
        assertEquals(analysis, insight.getAnalysis());


        verify(projectRepository)
                .findById(projectId);

        verify(analysisRepository)
                .findById(analysisId);

        verify(insightMapper)
                .toEntity(request);

        verify(insightRepository)
                .save(insight);

        verify(insightMapper)
                .toResponse(insight);
    }
    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateInsightRequest request =
                new CreateInsightRequest();

        request.setProjectId(projectId);
        request.setAnalysisId(UUID.randomUUID());


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> insightService.create(request)
        );


        verify(analysisRepository, never())
                .findById(any());

        verify(insightRepository, never())
                .save(any(Insight.class));
    }
    @Test
    void shouldThrowExceptionWhenAnalysisDoesNotExist() {

        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        CreateInsightRequest request =
                new CreateInsightRequest();

        request.setProjectId(projectId);
        request.setAnalysisId(analysisId);


        Project project = new Project();
        project.setId(projectId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(analysisRepository.findById(analysisId))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> insightService.create(request)
        );


        verify(insightRepository, never())
                .save(any(Insight.class));
    }
    @Test
    void shouldThrowExceptionWhenAnalysisDoesNotBelongToProject() {

        // Arrange
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        Project project = new Project();
        project.setId(projectId);

        Project otherProject = new Project();
        otherProject.setId(UUID.randomUUID());

        Analysis analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setProject(otherProject);


        CreateInsightRequest request =
                new CreateInsightRequest();

        request.setProjectId(projectId);
        request.setAnalysisId(analysisId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(analysisRepository.findById(analysisId))
                .thenReturn(Optional.of(analysis));


        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> insightService.create(request)
        );


        verify(insightRepository, never())
                .save(any(Insight.class));
    }
    @Test
    void shouldFindInsightById() {

        // Arrange
        UUID id = UUID.randomUUID();

        Insight insight =
                new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(insightRepository.findById(id))
                .thenReturn(Optional.of(insight));

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        InsightResponse result =
                insightService.getById(id);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);


        verify(insightRepository)
                .findById(id);

        verify(insightMapper)
                .toResponse(insight);
    }
    @Test
    void shouldThrowExceptionWhenInsightDoesNotExist() {

        // Arrange
        UUID id = UUID.randomUUID();

        when(insightRepository.findById(id))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> insightService.getById(id)
        );


        verify(insightMapper, never())
                .toResponse(any());
    }
    @Test
    void shouldReturnInsightsByProject() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        Insight insight =
                new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(insightRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(insight));

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        List<InsightResponse> result =
                insightService.getByProject(projectId);


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(insightRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);

        verify(insightMapper)
                .toResponse(insight);
    }
    @Test
    void shouldReturnInsightsByAnalysis() {

        // Arrange
        UUID analysisId = UUID.randomUUID();

        Insight insight =
                new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(insightRepository
                .findByAnalysisIdOrderByCreatedAtDesc(analysisId))
                .thenReturn(List.of(insight));

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        List<InsightResponse> result =
                insightService.getByAnalysis(analysisId);


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(insightRepository)
                .findByAnalysisIdOrderByCreatedAtDesc(analysisId);

        verify(insightMapper)
                .toResponse(insight);
    }
    @Test
    void shouldReturnInsightsByProjectAndType() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        InsightType type =
                InsightType.ARCHITECTURAL;

        Insight insight =
                new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(insightRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                ))
                .thenReturn(List.of(insight));

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        List<InsightResponse> result =
                insightService.getByProjectAndType(
                        projectId,
                        type
                );


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(insightRepository)
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                );

        verify(insightMapper)
                .toResponse(insight);
    }
    @Test
    void shouldReturnInsightsByProjectAndSeverity() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        InsightSeverity severity =
                InsightSeverity.CRITICAL;

        Insight insight =
                new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(insightRepository
                .findByProjectIdAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        severity
                ))
                .thenReturn(List.of(insight));

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        List<InsightResponse> result =
                insightService.getByProjectAndSeverity(
                        projectId,
                        severity
                );


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(insightRepository)
                .findByProjectIdAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        severity
                );

        verify(insightMapper)
                .toResponse(insight);
    }
    @Test
    void shouldReturnInsightsByProjectTypeAndSeverity() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        InsightType type =
                InsightType.SECURITY;

        InsightSeverity severity =
                InsightSeverity.CRITICAL;

        Insight insight =
                new Insight();

        InsightResponse response =
                mock(InsightResponse.class);


        when(insightRepository
                .findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        type,
                        severity
                ))
                .thenReturn(List.of(insight));

        when(insightMapper.toResponse(insight))
                .thenReturn(response);


        // Act
        List<InsightResponse> result =
                insightService.getByProjectAndTypeAndSeverity(
                        projectId,
                        type,
                        severity
                );


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(insightRepository)
                .findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        type,
                        severity
                );

        verify(insightMapper)
                .toResponse(insight);
    }

}
