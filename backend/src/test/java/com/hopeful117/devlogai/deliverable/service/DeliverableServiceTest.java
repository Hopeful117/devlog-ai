package com.hopeful117.devlogai.deliverable.service;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationRequest;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationResponse;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.deliverable.dto.CreateDeliverableRequest;
import com.hopeful117.devlogai.deliverable.entity.DeliverableType;
import com.hopeful117.devlogai.deliverable.entity.GeneratedDeliverable;
import com.hopeful117.devlogai.deliverable.repository.GeneratedDeliverableRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliverableServiceTest {
    @Mock ProjectRepository projects;
    @Mock AnalysisRepository analyses;
    @Mock InsightRepository insights;
    @Mock GeneratedDeliverableRepository deliverables;
    @Mock AIEngineClient aiEngine;
    @InjectMocks DeliverableServiceImpl service;

    @Test
    void generatesOnlyFromPersistedValidatedInsightsAndStoresTraceability() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID insightId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).name("DevLog AI").build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();
        Insight insight = Insight.builder().id(insightId).project(project).analysis(analysis)
                .type(InsightType.ARCHITECTURAL).severity(InsightSeverity.INFO)
                .title("Core").content("Spring Core").build();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(insights.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(insight));
        when(aiEngine.generateDeliverable(any())).thenReturn(new DeliverableGenerationResponse(
                "Description", "Content", "deliverable-generation-prompt-v1", "a".repeat(64),
                "mock", "deterministic-v1"));
        when(deliverables.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.generate(new CreateDeliverableRequest(projectId, null,
                DeliverableType.PROJECT_DESCRIPTION, "Engineers", "Concise", "en", null));

        ArgumentCaptor<DeliverableGenerationRequest> outbound = ArgumentCaptor.forClass(DeliverableGenerationRequest.class);
        verify(aiEngine).generateDeliverable(outbound.capture());
        assertEquals(List.of(insightId), outbound.getValue().validatedInsights().stream().map(
                DeliverableGenerationRequest.ValidatedInsightSnapshot::id).toList());
        assertEquals(List.of(insightId), result.sourceInsightIds());
        ArgumentCaptor<GeneratedDeliverable> saved = ArgumentCaptor.forClass(GeneratedDeliverable.class);
        verify(deliverables).save(saved.capture());
        assertEquals("a".repeat(64), saved.getValue().getPromptDigest());
    }

    @Test
    void refusesGenerationWithoutValidatedInsights() {
        UUID projectId = UUID.randomUUID();
        when(projects.findById(projectId)).thenReturn(Optional.of(Project.builder().id(projectId).build()));
        when(insights.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());
        assertThrows(ConflictException.class, () -> service.generate(new CreateDeliverableRequest(
                projectId, null, DeliverableType.README, "Engineers", "Concise", "en", null)));
        verifyNoInteractions(aiEngine);
    }
}
