package com.hopeful117.devlogai.decision.service;

import com.hopeful117.devlogai.decision.dto.request.CreateDecisionRequest;
import com.hopeful117.devlogai.decision.dto.response.DecisionResponse;
import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.mapper.DecisionMapper;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class DecisionServiceImpl implements  DecisionService{
    private final DecisionRepository decisionRepository;

    private final ProjectRepository projectRepository;

    private final DecisionMapper decisionMapper;



    @Override
    public DecisionResponse create(CreateDecisionRequest request) {

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Project",
                                request.getProjectId()
                        )
                );


        Decision decision = decisionMapper.toEntity(request);

        decision.setProject(project);


        Decision savedDecision =
                decisionRepository.save(decision);


        return decisionMapper.toResponse(savedDecision);
    }

    @Override
    public List<DecisionResponse> getByProject(UUID projectId) {
        return decisionRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(decisionMapper::toResponse)
                .toList();
    }

    @Override
    public DecisionResponse getById(UUID id) {


        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Decision",
                                id
                        )
                );


        return decisionMapper.toResponse(decision);
    }
}
