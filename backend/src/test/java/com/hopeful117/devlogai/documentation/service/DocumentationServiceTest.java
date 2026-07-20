package com.hopeful117.devlogai.documentation.service;

import com.hopeful117.devlogai.documentation.dto.request.CreateDocumentationRequest;
import com.hopeful117.devlogai.documentation.dto.response.DocumentationResponse;
import com.hopeful117.devlogai.documentation.entity.Documentation;
import com.hopeful117.devlogai.documentation.entity.DocumentationType;
import com.hopeful117.devlogai.documentation.mapper.DocumentationMapper;
import com.hopeful117.devlogai.documentation.repository.DocumentationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.mapper.ProjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentationServiceTest {

    @Mock
    DocumentationRepository documentationRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    DocumentationMapper documentationMapper;

    @InjectMocks
    DocumentationServiceImpl documentationService;

    @Test
    void shouldCreateDocumentationSuccessfully() {

        UUID projectId = UUID.randomUUID();

        CreateDocumentationRequest request =
                new CreateDocumentationRequest();

        request.setProjectId(projectId);
        request.setTitle("Architecture");
        request.setType(DocumentationType.ARCHITECTURE);
        request.setContent("# Architecture");


        Project project = new Project();

        Documentation documentation =
                new Documentation();

        DocumentationResponse response =
                mock(DocumentationResponse.class);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(documentationMapper.toEntity(request))
                .thenReturn(documentation);

        when(documentationRepository.save(documentation))
                .thenReturn(documentation);

        when(documentationMapper.toResponse(documentation))
                .thenReturn(response);


        DocumentationResponse result =
                documentationService.create(request);


        assertEquals(response, result);

        assertEquals(project, documentation.getProject());

        assertEquals(1, documentation.getVersion());


        verify(documentationRepository)
                .save(documentation);
    }
    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        UUID projectId = UUID.randomUUID();

        CreateDocumentationRequest request =
                new CreateDocumentationRequest();

        request.setProjectId(projectId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> documentationService.create(request)
        );


        verify(documentationRepository, never())
                .save(any());
    }
    @Test
    void shouldFindDocumentationById() {

        UUID id = UUID.randomUUID();

        Documentation documentation =
                new Documentation();

        DocumentationResponse response =
                mock(DocumentationResponse.class);


        when(documentationRepository.findById(id))
                .thenReturn(Optional.of(documentation));

        when(documentationMapper.toResponse(documentation))
                .thenReturn(response);


        DocumentationResponse result =
                documentationService.getById(id);


        assertEquals(response, result);


        verify(documentationRepository)
                .findById(id);
    }
    @Test
    void shouldReturnDocumentationByProject() {

        UUID projectId = UUID.randomUUID();

        Documentation documentation =
                new Documentation();

        DocumentationResponse response =
                mock(DocumentationResponse.class);


        when(documentationRepository
                .findByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(List.of(documentation));


        when(documentationMapper.toResponse(documentation))
                .thenReturn(response);


        List<DocumentationResponse> result =
                documentationService.getByProject(projectId);


        assertEquals(1, result.size());

        verify(documentationRepository)
                .findByProjectIdOrderByVersionDesc(projectId);
    }
    @Test
    void shouldReturnDocumentationByType() {

        UUID projectId = UUID.randomUUID();


        Documentation documentation =
                new Documentation();


        when(documentationRepository
                .findByProjectIdAndTypeOrderByVersionDesc(
                        projectId,
                        DocumentationType.API
                ))
                .thenReturn(List.of(documentation));


        when(documentationMapper.toResponse(documentation))
                .thenReturn(mock(DocumentationResponse.class));


        List<DocumentationResponse> result =
                documentationService.getByProjectAndType(
                        projectId,
                        DocumentationType.API
                );


        assertEquals(1, result.size());
    }
}
