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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ChallengeServiceImpl implements ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ProjectRepository projectRepository;
    private final ChallengeMapper challengeMapper;

    @Override
    public ChallengeResponse create(CreateChallengeRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Project",
                                request.getProjectId()
                        )
                );

        Challenge challenge = challengeMapper.toEntity(request);
        challenge.setProject(project);

        if (challenge.getStatus() == null) {
            challenge.setStatus(ChallengeStatus.OPEN);
        }

        Challenge savedChallenge = challengeRepository.save(challenge);

        return challengeMapper.toResponse(savedChallenge);
    }

    @Override
    public ChallengeResponse getById(UUID id) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Challenge",
                                id
                        )
                );

        return challengeMapper.toResponse(challenge);
    }

    @Override
    public List<ChallengeResponse> getByProject(UUID projectId) {
        return challengeRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(challengeMapper::toResponse)
                .toList();
    }

    @Override
    public ChallengeResponse update(UUID id, UpdateChallengeRequest request) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Challenge",
                                id
                        )
                );

        if (request.getTitle() != null) {
            challenge.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            challenge.setDescription(request.getDescription());
        }
        if (request.getImpact() != null) {
            challenge.setImpact(request.getImpact());
        }
        if (request.getStatus() != null) {
            challenge.setStatus(request.getStatus());
        }
        if (request.getResolution() != null) {
            challenge.setResolution(request.getResolution());
        }

        Challenge savedChallenge = challengeRepository.save(challenge);

        return challengeMapper.toResponse(savedChallenge);
    }
}
