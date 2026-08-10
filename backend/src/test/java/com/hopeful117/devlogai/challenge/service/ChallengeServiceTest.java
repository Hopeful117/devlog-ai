package com.hopeful117.devlogai.challenge.service;

import com.hopeful117.devlogai.challenge.dto.request.CreateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.request.UpdateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.response.ChallengeResponse;
import com.hopeful117.devlogai.challenge.entity.Challenge;
import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import com.hopeful117.devlogai.challenge.mapper.ChallengeMapper;
import com.hopeful117.devlogai.challenge.repository.ChallengeRepository;
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
class ChallengeServiceTest {

    @Mock
    ChallengeRepository challengeRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    ChallengeMapper challengeMapper;

    @InjectMocks
    ChallengeServiceImpl challengeService;

    @Test
    void shouldCreateChallengeSuccessfully() {
        UUID projectId = UUID.randomUUID();

        CreateChallengeRequest request = new CreateChallengeRequest();
        request.setProjectId(projectId);
        request.setTitle("Database migration failure");
        request.setDescription("Flyway fails on V33");
        request.setImpact("Blocks deployment");
        request.setStatus(ChallengeStatus.OPEN);

        Project project = new Project();
        Challenge challenge = new Challenge();
        ChallengeResponse response = new ChallengeResponse(
                UUID.randomUUID(),
                projectId,
                "Database migration failure",
                "Flyway fails on V33",
                "Blocks deployment",
                ChallengeStatus.OPEN,
                null,
                null,
                null
        );

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(challengeMapper.toEntity(request))
                .thenReturn(challenge);
        when(challengeRepository.save(challenge))
                .thenReturn(challenge);
        when(challengeMapper.toResponse(challenge))
                .thenReturn(response);

        ChallengeResponse result = challengeService.create(request);

        assertNotNull(result);
        assertEquals(response, result);
        assertEquals(project, challenge.getProject());
        assertEquals(ChallengeStatus.OPEN, challenge.getStatus());

        verify(projectRepository).findById(projectId);
        verify(challengeMapper).toEntity(request);
        verify(challengeRepository).save(challenge);
        verify(challengeMapper).toResponse(challenge);
    }

    @Test
    void shouldThrowExceptionWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();

        CreateChallengeRequest request = new CreateChallengeRequest();
        request.setProjectId(projectId);

        when(projectRepository.findById(projectId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> challengeService.create(request)
        );

        verify(projectRepository).findById(projectId);
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void shouldReturnChallengesForProject() {
        UUID projectId = UUID.randomUUID();

        Challenge challenge = new Challenge();
        ChallengeResponse response = new ChallengeResponse(
                UUID.randomUUID(),
                projectId,
                "Architecture bottleneck",
                "Monolith too slow",
                "High latency",
                ChallengeStatus.OPEN,
                null,
                null,
                null
        );

        when(challengeRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(challenge));
        when(challengeMapper.toResponse(challenge))
                .thenReturn(response);

        List<ChallengeResponse> result =
                challengeService.getByProject(projectId);

        assertEquals(1, result.size());
        assertEquals(response, result.get(0));

        verify(challengeRepository)
                .findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Test
    void shouldFindChallengeByIdSuccessfully() {
        UUID id = UUID.randomUUID();

        Challenge challenge = new Challenge();
        ChallengeResponse response = new ChallengeResponse(
                id,
                UUID.randomUUID(),
                "Test failure",
                "Flaky test",
                "Blocks CI",
                ChallengeStatus.OPEN,
                null,
                null,
                null
        );

        when(challengeRepository.findById(id))
                .thenReturn(Optional.of(challenge));
        when(challengeMapper.toResponse(challenge))
                .thenReturn(response);

        ChallengeResponse result = challengeService.getById(id);

        assertEquals(response, result);

        verify(challengeRepository).findById(id);
        verify(challengeMapper).toResponse(challenge);
    }

    @Test
    void shouldThrowExceptionWhenChallengeDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(challengeRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> challengeService.getById(id)
        );

        verify(challengeRepository).findById(id);
        verify(challengeMapper, never()).toResponse(any());
    }

    @Test
    void shouldUpdateChallengeSuccessfully() {
        UUID id = UUID.randomUUID();

        Challenge challenge = new Challenge();
        challenge.setId(id);
        challenge.setTitle("Old title");
        challenge.setStatus(ChallengeStatus.OPEN);

        UpdateChallengeRequest request = new UpdateChallengeRequest();
        request.setTitle("Updated title");
        request.setStatus(ChallengeStatus.RESOLVED);
        request.setResolution("Fixed by migrating to V34");

        ChallengeResponse response = new ChallengeResponse(
                id,
                UUID.randomUUID(),
                "Updated title",
                null,
                null,
                ChallengeStatus.RESOLVED,
                "Fixed by migrating to V34",
                null,
                null
        );

        when(challengeRepository.findById(id))
                .thenReturn(Optional.of(challenge));
        when(challengeRepository.save(challenge))
                .thenReturn(challenge);
        when(challengeMapper.toResponse(challenge))
                .thenReturn(response);

        ChallengeResponse result = challengeService.update(id, request);

        assertEquals(response, result);
        assertEquals("Updated title", challenge.getTitle());
        assertEquals(ChallengeStatus.RESOLVED, challenge.getStatus());
        assertEquals("Fixed by migrating to V34", challenge.getResolution());

        verify(challengeRepository).findById(id);
        verify(challengeRepository).save(challenge);
        verify(challengeMapper).toResponse(challenge);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentChallenge() {
        UUID id = UUID.randomUUID();

        UpdateChallengeRequest request = new UpdateChallengeRequest();
        request.setTitle("Updated");

        when(challengeRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> challengeService.update(id, request)
        );

        verify(challengeRepository).findById(id);
        verify(challengeRepository, never()).save(any());
    }
}
