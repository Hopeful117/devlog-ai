package com.hopeful117.devlogai.deliverable.service;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationRequest;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.deliverable.dto.CreateDeliverableRequest;
import com.hopeful117.devlogai.deliverable.dto.DeliverableResponse;
import com.hopeful117.devlogai.deliverable.entity.GeneratedDeliverable;
import com.hopeful117.devlogai.deliverable.repository.GeneratedDeliverableRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliverableServiceImpl implements DeliverableService {
    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;
    private final InsightRepository insightRepository;
    private final GeneratedDeliverableRepository deliverableRepository;
    private final AIEngineClient aiEngineClient;

    @Override
    @Transactional
    public DeliverableResponse generate(CreateDeliverableRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Project", request.projectId()));
        Analysis analysis = request.analysisId() == null ? null : analysisRepository.findById(request.analysisId())
                .orElseThrow(() -> new EntityNotFoundException("Analysis", request.analysisId()));
        if (analysis != null && !analysis.getProject().getId().equals(project.getId())) {
            throw new ConflictException("Analysis does not belong to the requested Project.");
        }
        List<Insight> insights = analysis == null
                ? insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(project.getId())
                : insightRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(analysis.getId());
        if (insights.isEmpty()) {
            throw new ConflictException("At least one validated Insight is required to generate a deliverable.");
        }

        UUID requestId = UUID.randomUUID();
        var generation = aiEngineClient.generateDeliverable(new DeliverableGenerationRequest(
                requestId, project.getId(), analysis == null ? null : analysis.getId(), request.type(),
                request.audience().trim(), request.style().trim(), request.language().trim(),
                normalize(request.additionalGuidance()), insights.stream().map(this::snapshot).toList()
        ));
        GeneratedDeliverable saved = deliverableRepository.save(GeneratedDeliverable.builder()
                .project(project).analysis(analysis).type(request.type())
                .audience(request.audience().trim()).style(request.style().trim())
                .language(request.language().trim()).additionalGuidance(normalize(request.additionalGuidance()))
                .title(generation.title()).content(generation.content())
                .promptVersion(generation.promptVersion()).promptDigest(generation.promptDigest())
                .provider(generation.provider()).modelIdentifier(generation.modelIdentifier())
                .generatedAt(Instant.now()).sourceInsights(new LinkedHashSet<>(insights)).build());
        return response(saved);
    }

    @Override @Transactional(readOnly = true)
    public DeliverableResponse getById(UUID id) {
        return response(deliverableRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Deliverable", id)));
    }

    @Override @Transactional(readOnly = true)
    public List<DeliverableResponse> getByProject(UUID projectId) {
        return deliverableRepository.findByProjectIdOrderByGeneratedAtDesc(projectId).stream()
                .map(this::response).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<DeliverableResponse> getByAnalysis(UUID analysisId) {
        return deliverableRepository.findByAnalysisIdOrderByGeneratedAtDesc(analysisId).stream()
                .map(this::response).toList();
    }

    private DeliverableGenerationRequest.ValidatedInsightSnapshot snapshot(Insight insight) {
        return new DeliverableGenerationRequest.ValidatedInsightSnapshot(
                insight.getId(), insight.getAnalysis().getId(), insight.getType().name(),
                insight.getSeverity().name(), insight.getTitle(), insight.getContent());
    }

    private DeliverableResponse response(GeneratedDeliverable value) {
        return new DeliverableResponse(value.getId(), value.getProject().getId(),
                value.getAnalysis() == null ? null : value.getAnalysis().getId(), value.getType(),
                value.getAudience(), value.getStyle(), value.getLanguage(), value.getAdditionalGuidance(),
                value.getTitle(), value.getContent(), value.getPromptVersion(), value.getPromptDigest(),
                value.getProvider(), value.getModelIdentifier(), value.getGeneratedAt(),
                value.getSourceInsights().stream().map(Insight::getId).sorted().toList());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
