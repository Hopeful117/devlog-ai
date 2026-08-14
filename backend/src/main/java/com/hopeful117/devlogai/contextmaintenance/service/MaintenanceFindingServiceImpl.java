package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceFindingMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceFindingServiceImpl implements MaintenanceFindingService {

    private final ProjectRepository projectRepository;
    private final MaintenanceFindingRepository repository;
    private final MaintenanceFindingMapper mapper;

    @Override
    @Transactional
    public MaintenanceFindingResponse create(UUID projectId, CreateMaintenanceFindingRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        MaintenanceFinding finding = MaintenanceFinding.builder()
                .project(project)
                .contextSurface(request.contextSurface())
                .issueType(request.issueType())
                .severity(request.severity())
                .status(MaintenanceFindingStatus.OPEN)
                .suggestedAction(request.suggestedAction())
                .humanReviewRequired(request.humanReviewRequired())
                .summary(request.summary().trim())
                .details(normalizeDetails(request.details()))
                .build();

        return mapper.toResponse(repository.save(finding));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceFindingResponse> getByProject(UUID projectId) {
        ensureProjectExists(projectId);
        return mapper.toResponse(repository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId));
    }

    @Override
    @Transactional
    public MaintenanceFindingResponse updateStatus(UUID projectId, UUID findingId, MaintenanceFindingStatus status) {
        ensureProjectExists(projectId);
        MaintenanceFinding finding = repository.findByIdAndProject_Id(findingId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceFinding", findingId));
        finding.setStatus(status);
        return mapper.toResponse(repository.save(finding));
    }

    private void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
    }

    private String normalizeDetails(String details) {
        if (details == null) {
            return null;
        }
        String normalized = details.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
