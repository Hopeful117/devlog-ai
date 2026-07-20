package com.hopeful117.devlogai.artifact.service;

import com.hopeful117.devlogai.artifact.dto.request.CreateArtifactRequest;
import com.hopeful117.devlogai.artifact.dto.response.ArtifactResponse;
import com.hopeful117.devlogai.artifact.entity.Artifact;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.mapper.ArtifactMapper;
import com.hopeful117.devlogai.artifact.repository.ArtifactRepository;
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
public class ArtifactServiceTest {

    @Mock
    ArtifactRepository artifactRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    ArtifactMapper artifactMapper;

    @InjectMocks
    ArtifactServiceImpl artifactService;

    @Test
    void shouldCreateArtifactSuccessfully() {

        UUID projectId = UUID.randomUUID();

        CreateArtifactRequest request =
                new CreateArtifactRequest();

        request.setProjectId(projectId);
        request.setName("RiskEngine");
        request.setType(ArtifactType.CODE);


        Project project = new Project();

        Artifact artifact = new Artifact();

        ArtifactResponse response =
                new ArtifactResponse(
                        UUID.randomUUID(),
                        projectId,
                        "RiskEngine",
                        ArtifactType.CODE,
                        null,
                        null,
                        null,
                        null
                );


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(artifactMapper.toEntity(request))
                .thenReturn(artifact);

        when(artifactRepository.save(artifact))
                .thenReturn(artifact);

        when(artifactMapper.toResponse(artifact))
                .thenReturn(response);


        ArtifactResponse result =
                artifactService.create(request);


        assertNotNull(result);
        assertEquals(response, result);

        assertEquals(project, artifact.getProject());


        verify(projectRepository)
                .findById(projectId);

        verify(artifactMapper)
                .toEntity(request);

        verify(artifactRepository)
                .save(artifact);

        verify(artifactMapper)
                .toResponse(artifact);
    }

    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        UUID projectId = UUID.randomUUID();

        CreateArtifactRequest request =
                new CreateArtifactRequest();

        request.setProjectId(projectId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> artifactService.create(request)
        );


        verify(projectRepository)
                .findById(projectId);


        verify(artifactRepository, never())
                .save(any());
    }

    @Test
    void shouldReturnArtifactsForProject() {

        UUID projectId = UUID.randomUUID();

        Artifact artifact = new Artifact();

        ArtifactResponse response =
                new ArtifactResponse(
                        UUID.randomUUID(),
                        projectId,
                        "docker-compose.yml",
                        ArtifactType.CONFIGURATION,
                        null,
                        null,
                        null,
                        null
                );


        when(artifactRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(artifact));


        when(artifactMapper.toResponse(artifact))
                .thenReturn(response);


        List<ArtifactResponse> result =
                artifactService.getByProject(projectId);


        assertEquals(1, result.size());
        assertEquals(response, result.get(0));


        verify(artifactRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Test
    void shouldReturnArtifactsByType() {

        UUID projectId = UUID.randomUUID();

        Artifact artifact = new Artifact();

        ArtifactResponse response =
                new ArtifactResponse(
                        UUID.randomUUID(),
                        projectId,
                        "Dockerfile",
                        ArtifactType.INFRASTRUCTURE,
                        null,
                        null,
                        null,
                        null
                );


        when(artifactRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        ArtifactType.INFRASTRUCTURE
                ))
                .thenReturn(List.of(artifact));


        when(artifactMapper.toResponse(artifact))
                .thenReturn(response);


        List<ArtifactResponse> result =
                artifactService.getByProjectAndType(
                        projectId,
                        ArtifactType.INFRASTRUCTURE
                );


        assertEquals(1, result.size());

        verify(artifactRepository)
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        ArtifactType.INFRASTRUCTURE
                );
    }

    @Test
    void shouldFindArtifactByIdSuccessfully() {

        UUID id = UUID.randomUUID();

        Artifact artifact = new Artifact();

        ArtifactResponse response =
                new ArtifactResponse(
                        id,
                        UUID.randomUUID(),
                        "Architecture.md",
                        ArtifactType.DOCUMENTATION,
                        null,
                        null,
                        null,
                        null
                );


        when(artifactRepository.findById(id))
                .thenReturn(Optional.of(artifact));


        when(artifactMapper.toResponse(artifact))
                .thenReturn(response);


        ArtifactResponse result =
                artifactService.getById(id);


        assertEquals(response, result);


        verify(artifactRepository)
                .findById(id);

        verify(artifactMapper)
                .toResponse(artifact);
    }

    @Test
    void shouldThrowExceptionWhenArtifactDoesNotExist() {

        UUID id = UUID.randomUUID();


        when(artifactRepository.findById(id))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> artifactService.getById(id)
        );


        verify(artifactRepository)
                .findById(id);


        verify(artifactMapper, never())
                .toResponse(any());
    }
}
