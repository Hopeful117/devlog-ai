package com.hopeful117.devlogai.decision.service;

import com.hopeful117.devlogai.decision.dto.request.CreateDecisionRequest;
import com.hopeful117.devlogai.decision.dto.response.DecisionResponse;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.mapper.DecisionMapper;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.project.service.ProjectService;
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
public class DecisionServiceTest {
    @Mock
    DecisionRepository decisionRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    DecisionMapper decisionMapper;

    @InjectMocks
    DecisionServiceImpl decisionService;

    @Test
    void shouldCreateDecisionSuccessfully() {

        UUID projectId = UUID.randomUUID();

        CreateDecisionRequest request = new CreateDecisionRequest();

        request.setProjectId(projectId);
        request.setTitle("Adoption de MapStruct");
        request.setContext("Les mappings manuels deviennent difficiles.");
        request.setChoice("Utiliser MapStruct");
        request.setRationale("Réduire le code répétitif");


        Project project = new Project();

        Decision decision = new Decision();

        DecisionResponse response =
                new DecisionResponse(
                        UUID.randomUUID(),
                        projectId,
                        "Adoption de MapStruct",
                        "Les mappings manuels deviennent difficiles.",
                        "Utiliser MapStruct",
                        "Réduire le code répétitif",
                        null,
                        null,
                        null
                );


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));

        when(decisionMapper.toEntity(request))
                .thenReturn(decision);

        when(decisionRepository.save(decision))
                .thenReturn(decision);

        when(decisionMapper.toResponse(decision))
                .thenReturn(response);


        DecisionResponse result =
                decisionService.create(request);


        assertNotNull(result);
        assertEquals(response, result);

        assertEquals(project, decision.getProject());


        verify(projectRepository)
                .findById(projectId);

        verify(decisionMapper)
                .toEntity(request);

        verify(decisionRepository)
                .save(decision);

        verify(decisionMapper)
                .toResponse(decision);
    }
    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {

        UUID projectId = UUID.randomUUID();

        CreateDecisionRequest request =
                new CreateDecisionRequest();

        request.setProjectId(projectId);


        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> decisionService.create(request)
        );


        verify(projectRepository)
                .findById(projectId);

        verify(decisionRepository, never())
                .save(any());
    }
    @Test
    void shouldReturnDecisionsForProject() {

        UUID projectId = UUID.randomUUID();

        Decision decision = new Decision();

        DecisionResponse response =
                new DecisionResponse(
                        UUID.randomUUID(),
                        projectId,
                        "Architecture choice",
                        "Context",
                        "Choice",
                        "Reason",
                        null,
                        null,
                        null
                );


        when(decisionRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(decision));


        when(decisionMapper.toResponse(decision))
                .thenReturn(response);


        List<DecisionResponse> result =
                decisionService.getByProject(projectId);


        assertEquals(1, result.size());
        assertEquals(response, result.get(0));


        verify(decisionRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);
    }
    @Test
    void shouldFindDecisionByIdSuccessfully() {

        UUID id = UUID.randomUUID();

        Decision decision = new Decision();

        DecisionResponse response =
                new DecisionResponse(
                        id,
                        UUID.randomUUID(),
                        "Choice",
                        "Context",
                        "Decision",
                        "Reason",
                        null,
                        null,
                        null
                );


        when(decisionRepository.findById(id))
                .thenReturn(Optional.of(decision));

        when(decisionMapper.toResponse(decision))
                .thenReturn(response);


        DecisionResponse result =
                decisionService.getById(id);


        assertEquals(response, result);


        verify(decisionRepository)
                .findById(id);

        verify(decisionMapper)
                .toResponse(decision);
    }
    @Test
    void shouldThrowExceptionWhenDecisionDoesNotExist() {

        UUID id = UUID.randomUUID();

        when(decisionRepository.findById(id))
                .thenReturn(Optional.empty());


        assertThrows(
                EntityNotFoundException.class,
                () -> decisionService.getById(id)
        );


        verify(decisionRepository)
                .findById(id);

        verify(decisionMapper, never())
                .toResponse(any());
    }
}
