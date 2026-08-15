package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateAuditResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.mapper.InsightMapper;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.service.KnowledgeRelationService;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightServiceImpl implements InsightService{

    private final InsightRepository insightRepository;

    private final InsightMapper insightMapper;
    private final TrustedKnowledgeDuplicateAuditService trustedKnowledgeDuplicateAuditService;
    private final KnowledgeRelationService knowledgeRelationService;


    @Override
    public InsightResponse getById(UUID id) {

        Insight insight =
                insightRepository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Insight",
                                        id
                                )
                        );

        return insightMapper.toResponse(insight);
    }


    @Override
    public List<InsightResponse> getByProject(
            UUID projectId) {

        return insightRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByAnalysis(
            UUID analysisId) {

        return insightRepository
                .findByAnalysisIdOrderByCreatedAtDesc(analysisId)
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByProjectAndType(
            UUID projectId,
            InsightType type) {

        return insightRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                )
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByProjectAndSeverity(
            UUID projectId,
            InsightSeverity severity) {

        return insightRepository
                .findByProjectIdAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        severity
                )
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByProjectAndTypeAndSeverity(
            UUID projectId,
            InsightType type,
            InsightSeverity severity) {

        return insightRepository
                .findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        type,
                        severity
                )
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }

    @Override
    public InsightDuplicateAuditResponse getDuplicateAudit(UUID projectId) {
        return trustedKnowledgeDuplicateAuditService.audit(projectId);
    }

    @Override
    @Transactional
    public InsightResponse archiveInsight(UUID insightId) {
        Insight insight = insightRepository.findById(insightId)
                .orElseThrow(() -> new EntityNotFoundException("Insight", insightId));
        insight.setStatus(InsightStatus.ARCHIVED);
        return insightMapper.toResponse(insightRepository.save(insight));
    }

    @Override
    @Transactional
    public InsightResponse supersedeInsight(UUID insightId, UUID canonicalInsightId) {
        Insight insight = insightRepository.findById(insightId)
                .orElseThrow(() -> new EntityNotFoundException("Insight", insightId));
        Insight canonical = insightRepository.findById(canonicalInsightId)
                .orElseThrow(() -> new EntityNotFoundException("Insight", canonicalInsightId));

        insight.setStatus(InsightStatus.SUPERSEDED);
        InsightResponse response = insightMapper.toResponse(insightRepository.save(insight));

        try {
            knowledgeRelationService.create(CreateKnowledgeRelationRequest.builder()
                    .projectId(canonical.getProject().getId())
                    .sourceEntityType(EntityType.INSIGHT)
                    .sourceEntityId(insightId)
                    .targetEntityType(EntityType.INSIGHT)
                    .targetEntityId(canonicalInsightId)
                    .relationType(KnowledgeRelationType.RESOLVES)
                    .description("Insight superseded during duplicate resolution")
                    .build());
        } catch (Exception e) {
            log.warn("Failed to create RESOLVES relation for superseded insight {}: {}", insightId, e.getMessage());
        }

        return response;
    }

}
