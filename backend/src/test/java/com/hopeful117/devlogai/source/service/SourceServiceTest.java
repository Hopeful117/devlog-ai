package com.hopeful117.devlogai.source.service;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.dto.request.CreateSourceRequest;
import com.hopeful117.devlogai.source.dto.response.SourceResponse;
import com.hopeful117.devlogai.source.entity.GitProvider;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.mapper.SourceMapper;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SourceMapper sourceMapper;

    @InjectMocks
    private SourceServiceImpl sourceService;

    @Test
    void shouldCreateAnActiveGitRepositorySource() {
        UUID projectId = UUID.randomUUID();
        CreateSourceRequest request = request(projectId);
        Project project = Project.builder().id(projectId).build();
        Source source = Source.builder().type(SourceType.GIT_REPOSITORY).build();
        SourceResponse response = response(UUID.randomUUID(), projectId, true);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(sourceMapper.toEntity(request)).thenReturn(source);
        when(sourceRepository.save(source)).thenReturn(source);
        when(sourceMapper.toResponse(source)).thenReturn(response);

        SourceResponse result = sourceService.create(request);

        assertEquals(response, result);
        assertEquals(project, source.getProject());
        assertTrue(source.isActive());
        verify(sourceRepository).save(source);
    }

    @Test
    void shouldRejectCreationForUnknownProject() {
        UUID projectId = UUID.randomUUID();
        CreateSourceRequest request = request(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> sourceService.create(request));

        verify(sourceMapper, never()).toEntity(request);
        verify(sourceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRetrieveSourceById() {
        UUID sourceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId).build();
        SourceResponse response = response(sourceId, projectId, true);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(sourceMapper.toResponse(source)).thenReturn(response);

        assertEquals(response, sourceService.getById(sourceId));
    }

    @Test
    void shouldRejectUnknownSource() {
        UUID sourceId = UUID.randomUUID();
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> sourceService.getById(sourceId));

        verify(sourceMapper, never()).toResponse(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRetrieveOnlySourcesRequestedForProject() {
        UUID projectId = UUID.randomUUID();
        Source first = Source.builder().id(UUID.randomUUID()).build();
        Source second = Source.builder().id(UUID.randomUUID()).build();
        SourceResponse firstResponse = response(first.getId(), projectId, true);
        SourceResponse secondResponse = response(second.getId(), projectId, false);

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(sourceRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId))
                .thenReturn(List.of(first, second));
        when(sourceMapper.toResponse(first)).thenReturn(firstResponse);
        when(sourceMapper.toResponse(second)).thenReturn(secondResponse);

        List<SourceResponse> result = sourceService.getByProject(projectId);

        assertEquals(List.of(firstResponse, secondResponse), result);
        verify(sourceRepository).findByProjectIdOrderByCreatedAtDescIdDesc(projectId);
    }

    @Test
    void shouldRejectRetrievalForUnknownProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> sourceService.getByProject(projectId));

        verify(sourceRepository, never())
                .findByProjectIdOrderByCreatedAtDescIdDesc(projectId);
    }

    @Test
    void shouldDeactivateSourceWithoutDeletingIt() {
        UUID sourceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Source source = Source.builder().id(sourceId).active(true).build();
        SourceResponse response = response(sourceId, projectId, false);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(sourceRepository.save(source)).thenReturn(source);
        when(sourceMapper.toResponse(source)).thenReturn(response);

        SourceResponse result = sourceService.setActive(sourceId, false);

        assertFalse(source.isActive());
        assertEquals(response, result);
        verify(sourceRepository).save(source);
    }

    private CreateSourceRequest request(UUID projectId) {
        return new CreateSourceRequest(
                projectId,
                SourceType.GIT_REPOSITORY,
                "devlog-ai",
                "https://github.com/example/devlog-ai.git",
                "main",
                GitProvider.GITHUB
        );
    }

    private SourceResponse response(UUID sourceId, UUID projectId, boolean active) {
        return new SourceResponse(
                sourceId,
                projectId,
                SourceType.GIT_REPOSITORY,
                "devlog-ai",
                "https://github.com/example/devlog-ai.git",
                "main",
                GitProvider.GITHUB,
                active,
                null,
                null,
                null
        );
    }
}
