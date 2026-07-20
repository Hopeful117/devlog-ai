package com.hopeful117.devlogai.knowledge.service;

import com.hopeful117.devlogai.knowledge.dto.request.CreateKnowledgeEventRequest;
import com.hopeful117.devlogai.knowledge.dto.response.KnowledgeEventResponse;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.mapper.KnowledgeEventMapper;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.sqm.EntityTypeException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeEventServiceImpl implements KnowledgeEventService {
    private final KnowledgeEventRepository knowledgeEventRepository;
    private final KnowledgeEventMapper knowledgeEventMapper;
    private final ProjectRepository projectRepository;

    @Override
    public KnowledgeEventResponse create(CreateKnowledgeEventRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Project",
                                request.getProjectId()
                        )
                );


        KnowledgeEvent event = knowledgeEventMapper.toEntity(request);

        event.setProject(project);


        KnowledgeEvent savedEvent =
                knowledgeEventRepository.save(event);


        return knowledgeEventMapper.toResponse(savedEvent);
    }

    @Override
    public List<KnowledgeEventResponse> getByProject(UUID projectId) {
        List<KnowledgeEvent> events =
                knowledgeEventRepository
                        .findByProjectIdOrderByCreatedAtDesc(projectId);

        return events.stream()
                .map(knowledgeEventMapper::toResponse)
                .toList();
    }


    @Override
    public KnowledgeEventResponse getById(UUID id) {
        return knowledgeEventMapper.toResponse(knowledgeEventRepository.findById(id).orElseThrow(()->new EntityNotFoundException("knowledge event",id.toString())));
    }
}
