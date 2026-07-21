package com.hopeful117.devlogai.milestone.service;

import com.hopeful117.devlogai.milestone.dto.request.CreateMilestoneRequest;
import com.hopeful117.devlogai.milestone.dto.response.MilestoneResponse;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.mapper.MilestoneMapper;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {
    private final MilestoneRepository milestoneRepository;

    private final ProjectRepository projectRepository;

    private final MilestoneMapper milestoneMapper;


    @Override
    public MilestoneResponse create(
            CreateMilestoneRequest request) {

        Project project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Project",
                                        request.getProjectId()
                                )
                        );

        Milestone milestone =
                milestoneMapper.toEntity(request);

        milestone.setProject(project);
        milestone.setStatus(MilestoneStatus.PLANNED);

        Milestone saved =
                milestoneRepository.save(milestone);

        return milestoneMapper.toResponse(saved);
    }


    @Override
    public MilestoneResponse getById(UUID id) {

        Milestone milestone =
                milestoneRepository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Milestone",
                                        id
                                )
                        );

        return milestoneMapper.toResponse(milestone);
    }


    @Override
    public List<MilestoneResponse> getByProject(
            UUID projectId) {

        return milestoneRepository
                .findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .map(milestoneMapper::toResponse)
                .toList();
    }


    @Override
    public List<MilestoneResponse> getByProjectAndStatus(
            UUID projectId,
            MilestoneStatus status) {

        return milestoneRepository
                .findByProjectIdAndStatusOrderByStartedAtDesc(
                        projectId,
                        status
                )
                .stream()
                .map(milestoneMapper::toResponse)
                .toList();
    }
}
