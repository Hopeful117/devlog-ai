package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessment;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.mapper.MaintenanceAssessmentMapper;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceAssessmentRepository;
import com.hopeful117.devlogai.contextmaintenance.repository.MaintenanceFindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MaintenanceAssessmentServiceImpl implements MaintenanceAssessmentService {

    private final MaintenanceAssessmentRepository assessmentRepository;
    private final MaintenanceFindingRepository findingRepository;
    private final MaintenanceAssessmentMapper assessmentMapper;

    @Override
    public MaintenanceAssessmentResponse create(
            UUID projectId,
            CreateMaintenanceAssessmentRequest request
    ) {
        MaintenanceFinding finding = findingRepository
                .findByIdAndProject_Id(request.findingId(), projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Maintenance finding not found: " + request.findingId()
                ));

        MaintenanceAssessment assessment = MaintenanceAssessment.builder()
                .finding(finding)
                .projectId(projectId)
                .confidenceLevel(request.confidenceLevel())
                .semanticClassification(request.semanticClassification())
                .recommendedAction(request.recommendedAction())
                .rationale(request.rationale())
                .supportingSignals(request.supportingSignals())
                .build();

        MaintenanceAssessment saved = assessmentRepository.save(assessment);
        return assessmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceAssessmentResponse> getByProject(UUID projectId) {
        return assessmentMapper.toResponse(
                assessmentRepository.findByProject_IdOrderByCreatedAtDescIdDesc(projectId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceAssessmentResponse> getByFinding(
            UUID projectId,
            UUID findingId
    ) {
        return assessmentMapper.toResponse(
                assessmentRepository.findByFinding_IdAndProject_IdOrderByCreatedAtDescIdDesc(
                        findingId,
                        projectId
                )
        );
    }
}
