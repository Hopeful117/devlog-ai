package com.hopeful117.devlogai.analysis.service;

import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.mapper.AnalysisMapper;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalysisServiceTest {

    @Mock
    AnalysisMapper analysisMapper;

    @Mock
    AnalysisRepository analysisRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    IntentCatalog intentCatalog;

    @InjectMocks
    AnalysisServiceImpl analysisService;

    @Test
    void shouldStartPendingAnalysis() {
        UUID id = UUID.randomUUID();
        Analysis analysis = new Analysis();
        analysis.setStatus(AnalysisStatus.PENDING);
        AnalysisResponse response = mock(AnalysisResponse.class);
        when(analysisRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(analysis));
        when(analysisRepository.save(analysis)).thenReturn(analysis);
        when(analysisMapper.toResponse(analysis)).thenReturn(response);

        assertSame(response, analysisService.start(id));
        assertEquals(AnalysisStatus.IN_PROGRESS, analysis.getStatus());
        assertNotNull(analysis.getStartedAt());
        assertNull(analysis.getCompletedAt());
    }

    @Test
    void shouldRejectStartingAnalysisThatIsNotPending() {
        UUID id = UUID.randomUUID();
        Analysis analysis = new Analysis();
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        when(analysisRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(analysis));

        assertThrows(ConflictException.class, () -> analysisService.start(id));
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void shouldFailInProgressAnalysis() {
        UUID id = UUID.randomUUID();
        Analysis analysis = new Analysis();
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        AnalysisResponse response = mock(AnalysisResponse.class);
        when(analysisRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(analysis));
        when(analysisRepository.save(analysis)).thenReturn(analysis);
        when(analysisMapper.toResponse(analysis)).thenReturn(response);

        assertSame(response, analysisService.fail(id));
        assertEquals(AnalysisStatus.FAILED, analysis.getStatus());
        assertNotNull(analysis.getCompletedAt());
    }


    @Test
    void shouldCreateAnalysisSuccessfully() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateAnalysisRequest request =
                new CreateAnalysisRequest();

        request.setProjectId(projectId);
        request.setType(AnalysisType.ARCHITECTURE_REVIEW);
        request.setIntentId("describe-project-v1");

        Project project = new Project();

        Analysis analysis = new Analysis();
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        analysis.setStartedAt(Instant.now());
        analysis.setCompletedAt(Instant.now());

        AnalysisResponse response =
                mock(AnalysisResponse.class);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(intentCatalog.resolve("describe-project-v1"))
                .thenReturn(new IntentDefinition("describe-project", "v1", "Describe",
                        List.of(InsightType.PROJECT_PRESENTATION), List.of("traceable"),
                        java.util.Map.of("type", "object"), "describe-project-prompt-v1"));

        when(analysisMapper.toEntity(request))
                .thenReturn(analysis);

        when(analysisRepository.save(analysis))
                .thenReturn(analysis);

        when(analysisMapper.toResponse(analysis))
                .thenReturn(response);


        // Act
        AnalysisResponse result =
                analysisService.create(request);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);

        assertEquals(project, analysis.getProject());

        assertEquals(
                AnalysisStatus.PENDING,
                analysis.getStatus()
        );

        assertNull(analysis.getStartedAt());
        assertNull(analysis.getCompletedAt());
        assertEquals("describe-project", analysis.getIntentId());
        assertEquals("v1", analysis.getIntentVersion());


        verify(projectRepository)
                .findById(projectId);

        verify(analysisMapper)
                .toEntity(request);

        verify(analysisRepository)
                .save(analysis);

        verify(analysisMapper)
                .toResponse(analysis);
    }

    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateAnalysisRequest request =
                new CreateAnalysisRequest();

        request.setProjectId(projectId);
        request.setType(AnalysisType.ARCHITECTURE_REVIEW);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> analysisService.create(request)
        );


        verify(analysisRepository, never())
                .save(any(Analysis.class));
    }
    @Test
    void shouldFindAnalysisById() {

        // Arrange
        UUID id = UUID.randomUUID();

        Analysis analysis =
                new Analysis();

        AnalysisResponse response =
                mock(AnalysisResponse.class);


        when(analysisRepository.findById(id))
                .thenReturn(Optional.of(analysis));

        when(analysisMapper.toResponse(analysis))
                .thenReturn(response);


        // Act
        AnalysisResponse result =
                analysisService.getById(id);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);


        verify(analysisRepository)
                .findById(id);

        verify(analysisMapper)
                .toResponse(analysis);
    }
    @Test
    void shouldThrowExceptionWhenAnalysisDoesNotExist() {

        // Arrange
        UUID id = UUID.randomUUID();

        when(analysisRepository.findById(id))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> analysisService.getById(id)
        );


        verify(analysisMapper, never())
                .toResponse(any());
    }
    @Test
    void shouldReturnAnalysesByProject() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        Analysis analysis =
                new Analysis();

        AnalysisResponse response =
                mock(AnalysisResponse.class);


        when(analysisRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(analysis));

        when(analysisMapper.toResponse(analysis))
                .thenReturn(response);


        // Act
        List<AnalysisResponse> result =
                analysisService.getByProject(projectId);


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(analysisRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);

        verify(analysisMapper)
                .toResponse(analysis);
    }
    @Test
    void shouldReturnAnalysesByProjectAndType() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        AnalysisType type =
                AnalysisType.ARCHITECTURE_REVIEW;

        Analysis analysis =
                new Analysis();

        AnalysisResponse response =
                mock(AnalysisResponse.class);


        when(analysisRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                ))
                .thenReturn(List.of(analysis));

        when(analysisMapper.toResponse(analysis))
                .thenReturn(response);


        // Act
        List<AnalysisResponse> result =
                analysisService.getByProjectAndType(
                        projectId,
                        type
                );


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(analysisRepository)
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                );

        verify(analysisMapper)
                .toResponse(analysis);
    }
    @Test
    void shouldReturnAnalysesByProjectAndStatus() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        AnalysisStatus status =
                AnalysisStatus.IN_PROGRESS;

        Analysis analysis =
                new Analysis();

        AnalysisResponse response =
                mock(AnalysisResponse.class);


        when(analysisRepository
                .findByProjectIdAndStatusOrderByCreatedAtDesc(
                        projectId,
                        status
                ))
                .thenReturn(List.of(analysis));

        when(analysisMapper.toResponse(analysis))
                .thenReturn(response);


        // Act
        List<AnalysisResponse> result =
                analysisService.getByProjectAndStatus(
                        projectId,
                        status
                );


        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());


        verify(analysisRepository)
                .findByProjectIdAndStatusOrderByCreatedAtDesc(
                        projectId,
                        status
                );

        verify(analysisMapper)
                .toResponse(analysis);
    }
}
