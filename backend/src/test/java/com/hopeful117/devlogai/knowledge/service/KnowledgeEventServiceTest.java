package com.hopeful117.devlogai.knowledge.service;

import com.hopeful117.devlogai.knowledge.dto.request.CreateKnowledgeEventRequest;
import com.hopeful117.devlogai.knowledge.dto.response.KnowledgeEventResponse;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.knowledge.mapper.KnowledgeEventMapper;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
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
public class KnowledgeEventServiceTest {
    @Mock
    private KnowledgeEventRepository knowledgeEventRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private KnowledgeEventMapper knowledgeEventMapper;

    @InjectMocks
    private KnowledgeEventServiceImpl knowledgeEventService;

    @Test
    void shouldCreateKnowledgeEventSuccessfully() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateKnowledgeEventRequest request =
                new CreateKnowledgeEventRequest();

        request.setProjectId(projectId);
        request.setTitle("Migration vers OpenFeign");
        request.setType(KnowledgeEventType.ARCHITECTURE);


        Project project = new Project();

        KnowledgeEvent event = new KnowledgeEvent();

        KnowledgeEventResponse response =
                new KnowledgeEventResponse(
                        UUID.randomUUID(),
                        projectId,
                        KnowledgeEventType.ARCHITECTURE,
                        "Migration vers OpenFeign",
                        null,
                        null,
                        null
                );


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(knowledgeEventMapper.toEntity(request))
                .thenReturn(event);

        when(knowledgeEventRepository.save(event))
                .thenReturn(event);

        when(knowledgeEventMapper.toResponse(event))
                .thenReturn(response);


        // Act
        KnowledgeEventResponse result =
                knowledgeEventService.create(request);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);


        verify(projectRepository)
                .findById(projectId);

        verify(knowledgeEventMapper)
                .toEntity(request);

        verify(knowledgeEventRepository)
                .save(event);

        verify(knowledgeEventMapper)
                .toResponse(event);


        assertEquals(project, event.getProject());
    }
    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        CreateKnowledgeEventRequest request =
                new CreateKnowledgeEventRequest();

        request.setProjectId(projectId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> knowledgeEventService.create(request)
        );


        verify(projectRepository)
                .findById(projectId);


        verify(knowledgeEventRepository, never())
                .save(any(KnowledgeEvent.class));
    }
    @Test
    void shouldReturnKnowledgeEventsForProject() {

        // Arrange
        UUID projectId = UUID.randomUUID();

        KnowledgeEvent event1 = new KnowledgeEvent();
        KnowledgeEvent event2 = new KnowledgeEvent();

        KnowledgeEventResponse response1 =
                new KnowledgeEventResponse(
                        UUID.randomUUID(),
                        projectId,
                        KnowledgeEventType.FEATURE,
                        "Feature 1",
                        null,
                        null,
                        null
                );

        KnowledgeEventResponse response2 =
                new KnowledgeEventResponse(
                        UUID.randomUUID(),
                        projectId,
                        KnowledgeEventType.BUG,
                        "Bug fix",
                        null,
                        null,
                        null
                );


        when(knowledgeEventRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(event1, event2));


        when(knowledgeEventMapper.toResponse(event1))
                .thenReturn(response1);

        when(knowledgeEventMapper.toResponse(event2))
                .thenReturn(response2);


        // Act
        List<KnowledgeEventResponse> result =
                knowledgeEventService.getByProject(projectId);


        // Assert
        assertEquals(2, result.size());

        assertEquals(response1, result.get(0));
        assertEquals(response2, result.get(1));


        verify(knowledgeEventRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);
    }
    @Test
    void shouldReturnEmptyListWhenProjectHasNoEvents() {

        UUID projectId = UUID.randomUUID();

        when(knowledgeEventRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of());


        List<KnowledgeEventResponse> result =
                knowledgeEventService.getByProject(projectId);


        assertNotNull(result);
        assertTrue(result.isEmpty());


        verify(knowledgeEventRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);
    }
    @Test
    void shouldFindKnowledgeEventByIdSuccessfully() {

        // Arrange
        UUID id = UUID.randomUUID();

        KnowledgeEvent event = new KnowledgeEvent();

        KnowledgeEventResponse response =
                new KnowledgeEventResponse(
                        id,
                        UUID.randomUUID(),
                        KnowledgeEventType.ARCHITECTURE,
                        "Migration OpenFeign",
                        "Replace RestTemplate",
                        null,
                        null
                );


        when(knowledgeEventRepository.findById(id))
                .thenReturn(Optional.of(event));

        when(knowledgeEventMapper.toResponse(event))
                .thenReturn(response);


        // Act
        KnowledgeEventResponse result =
                knowledgeEventService.getById(id);


        // Assert
        assertNotNull(result);
        assertEquals(response, result);


        verify(knowledgeEventRepository)
                .findById(id);

        verify(knowledgeEventMapper)
                .toResponse(event);
    }
    @Test
    void shouldThrowExceptionWhenKnowledgeEventDoesNotExist() {

        // Arrange
        UUID id = UUID.randomUUID();


        when(knowledgeEventRepository.findById(id))
                .thenReturn(Optional.empty());


        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> knowledgeEventService.getById(id)
        );


        verify(knowledgeEventRepository)
                .findById(id);


        verify(knowledgeEventMapper, never())
                .toResponse(any(KnowledgeEvent.class));
    }

}
