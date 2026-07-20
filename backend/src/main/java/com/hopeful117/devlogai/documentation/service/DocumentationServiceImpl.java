package com.hopeful117.devlogai.documentation.service;

import com.hopeful117.devlogai.documentation.dto.request.CreateDocumentationRequest;
import com.hopeful117.devlogai.documentation.dto.response.DocumentationResponse;
import com.hopeful117.devlogai.documentation.entity.Documentation;
import com.hopeful117.devlogai.documentation.entity.DocumentationType;
import com.hopeful117.devlogai.documentation.mapper.DocumentationMapper;
import com.hopeful117.devlogai.documentation.repository.DocumentationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@Service
@RequiredArgsConstructor
public class DocumentationServiceImpl implements DocumentationService {

    private final DocumentationRepository documentationRepository;

    private final ProjectRepository projectRepository;

    private final DocumentationMapper documentationMapper;

    @Override
    public DocumentationResponse create(CreateDocumentationRequest request) {
        Project project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Project",
                                        request.getProjectId()
                                )
                        );


        Documentation documentation =
                documentationMapper.toEntity(request);


        documentation.setProject(project);

        documentation.setVersion(1);


        Documentation saved =
                documentationRepository.save(documentation);


        return documentationMapper.toResponse(saved);

    }

    @Override
    public DocumentationResponse getById(UUID id) {
        Documentation documentation =
                documentationRepository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Documentation",
                                        id
                                )
                        );


        return documentationMapper.toResponse(documentation);
    }

    @Override
    public List<DocumentationResponse> getByProject(UUID projectId) {

        return documentationRepository
                .findByProjectIdOrderByVersionDesc(projectId)
                .stream()
                .map(documentationMapper::toResponse)
                .toList();
    }

    @Override
    public List<DocumentationResponse> getByProjectAndType(UUID projectId, DocumentationType type) {
            return documentationRepository
                .findByProjectIdAndTypeOrderByVersionDesc(
                        projectId,
                        type
                )
                .stream()
                .map(documentationMapper::toResponse)
                .toList();
    }
    }

