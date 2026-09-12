package com.hopeful117.devlogai.analysis.communication;

import com.hopeful117.devlogai.agentcommunication.AgentCommunication;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationIntent;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationPort;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.analysis.result.service.AnalysisResultQueryService;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisCommunicationUseCaseTest {

    @Mock
    private AnalysisService analysisService;
    @Mock
    private AnalysisResultQueryService analysisResultQueryService;
    @Mock
    private AgentCommunicationPort agentCommunicationPort;

    private AnalysisCommunicationUseCase useCase() {
        return new AnalysisCommunicationUseCase(analysisService, analysisResultQueryService, agentCommunicationPort);
    }

    @Test
    void communicatesSynthesisWhenPresent() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithSynthesis("Architecture Overview", "Section content", "NO_MATERIAL_DELTA"));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals("COMMUNICATED", response.status());
        assertEquals("DEVLOG", response.agent());
        assertEquals("INFORM", response.intent());
        assertEquals("Architecture Overview", response.title());
        assertEquals("Section content", response.body());

        ArgumentCaptor<AgentCommunication> captor = ArgumentCaptor.forClass(AgentCommunication.class);
        verify(agentCommunicationPort).communicate(captor.capture());
        assertEquals("DEVLOG", captor.getValue().agent());
        assertEquals(AgentCommunicationIntent.INFORM, captor.getValue().intent());
    }

    @Test
    void fallsBackToInsightWhenSynthesisAbsent() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithInsights(List.of(
                        insightSummary("Low severity", "Some detail", InsightSeverity.INFO),
                        insightSummary("Critical finding", "Critical detail", InsightSeverity.CRITICAL))));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals("COMMUNICATED", response.status());
        assertEquals("Critical finding", response.title());
        assertEquals("Critical detail", response.body());

        verify(agentCommunicationPort).communicate(any(AgentCommunication.class));
    }

    @Test
    void selectsHighestSeverityInsight() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithInsights(List.of(
                        insightSummary("Info insight", "Info detail", InsightSeverity.INFO),
                        insightSummary("Warning insight", "Warning detail", InsightSeverity.WARNING),
                        insightSummary("Critical insight", "Critical detail", InsightSeverity.CRITICAL))));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals("Critical insight", response.title());
        assertEquals("Critical detail", response.body());
    }

    @Test
    void silenceWhenNoSynthesisAndNoInsights() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(resultEmpty());

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals("SILENCE", response.status());
        assertNull(response.agent());
        assertNull(response.intent());
        assertNull(response.title());
        assertNull(response.body());

        verify(agentCommunicationPort, never()).communicate(any());
    }

    @Test
    void silenceWhenAnalysisNotCompleted() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(
                new AnalysisResponse(analysisId, UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW,
                        AnalysisStatus.IN_PROGRESS, null, null, Instant.now(), Instant.now()));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals("SILENCE", response.status());
        verify(agentCommunicationPort, never()).communicate(any());
        verify(analysisResultQueryService, never()).getResult(any());
    }

    @Test
    void doesNotModifyAnalysisState() {
        UUID analysisId = UUID.randomUUID();
        AnalysisResponse original = completedAnalysis(analysisId);
        when(analysisService.getById(analysisId)).thenReturn(original);
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(resultEmpty());

        useCase().execute(analysisId);

        verify(analysisService, never()).start(any());
        verify(analysisService, never()).fail(any());
    }

    @Test
    void agentIdentityIsDevlogNotInventedByPresence() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithSynthesis("Title", "Content", "NO_MATERIAL_DELTA"));

        useCase().execute(analysisId);

        ArgumentCaptor<AgentCommunication> captor = ArgumentCaptor.forClass(AgentCommunication.class);
        verify(agentCommunicationPort).communicate(captor.capture());
        assertEquals("DEVLOG", captor.getValue().agent());
    }

    @Test
    void truncatesLongTitle() {
        UUID analysisId = UUID.randomUUID();
        String longTitle = "A".repeat(100);
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithSynthesis(longTitle, "Content", "NO_MATERIAL_DELTA"));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals(50, response.title().length());
    }

    @Test
    void truncatesLongBody() {
        UUID analysisId = UUID.randomUUID();
        String longBody = "B".repeat(500);
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithSynthesis("Title", longBody, "NO_MATERIAL_DELTA"));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals(200, response.body().length());
    }

    @Test
    void noPresenceDependencyFromUseCase() {
        String[] packages = AnalysisCommunicationUseCase.class.getPackage().getName().split("\\.");
        String useCasePackage = String.join(".", packages);

        assertFalse(useCasePackage.contains("agentpresence"),
                "Use case must not depend on agentpresence package");
    }

    @Test
    void insightFallbackWithBlankContentIsSkipped() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.getById(analysisId)).thenReturn(completedAnalysis(analysisId));
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithInsights(List.of(
                        insightSummary("Title", "", InsightSeverity.CRITICAL),
                        insightSummary("Valid insight", "Valid content", InsightSeverity.INFO))));

        AnalysisCommunicationResponse response = useCase().execute(analysisId);

        assertEquals("COMMUNICATED", response.status());
        assertEquals("Valid insight", response.title());
    }

    // --- helpers ---

    private AnalysisResponse completedAnalysis(UUID id) {
        return new AnalysisResponse(id, UUID.randomUUID(), AnalysisType.ARCHITECTURE_REVIEW,
                "architecture-overview", "v2", AnalysisStatus.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
    }

    private AnalysisResultResponse resultWithSynthesis(String title, String sectionContent, String deltaConclusion) {
        return new AnalysisResultResponse(
                new AnalysisResultResponse.AnalysisHeader(
                        UUID.randomUUID(), UUID.randomUUID(), null, null,
                        "architecture-overview", "v2", AnalysisStatus.COMPLETED,
                        null, null, null, null, null, null),
                AnalysisResultResponse.ExecutionStatus.ofSuccess(),
                new AnalysisResultResponse.ProposalsSection(0, Map.of(), Map.of(), List.of()),
                new AnalysisResultResponse.InsightsSection(0, List.of()),
                new AnalysisResultResponse.DeliverablesSection(0, List.of()),
                AnalysisResultResponse.emptyEvidence(),
                new AnalysisResultResponse.SynthesisSection(
                        title,
                        List.of(new AnalysisResultResponse.SynthesisItem("section", sectionContent)),
                        deltaConclusion,
                        List.of()),
                List.of());
    }

    private AnalysisResultResponse resultWithInsights(List<AnalysisResultResponse.InsightSummary> insights) {
        return new AnalysisResultResponse(
                new AnalysisResultResponse.AnalysisHeader(
                        UUID.randomUUID(), UUID.randomUUID(), null, null,
                        "architecture-overview", "v2", AnalysisStatus.COMPLETED,
                        null, null, null, null, null, null),
                AnalysisResultResponse.ExecutionStatus.ofSuccess(),
                new AnalysisResultResponse.ProposalsSection(0, Map.of(), Map.of(), List.of()),
                new AnalysisResultResponse.InsightsSection(insights.size(), insights),
                new AnalysisResultResponse.DeliverablesSection(0, List.of()),
                AnalysisResultResponse.emptyEvidence(),
                null,
                List.of());
    }

    private AnalysisResultResponse resultEmpty() {
        return new AnalysisResultResponse(
                new AnalysisResultResponse.AnalysisHeader(
                        UUID.randomUUID(), UUID.randomUUID(), null, null,
                        "architecture-overview", "v2", AnalysisStatus.COMPLETED,
                        null, null, null, null, null, null),
                AnalysisResultResponse.ExecutionStatus.ofSuccess(),
                new AnalysisResultResponse.ProposalsSection(0, Map.of(), Map.of(), List.of()),
                new AnalysisResultResponse.InsightsSection(0, List.of()),
                new AnalysisResultResponse.DeliverablesSection(0, List.of()),
                AnalysisResultResponse.emptyEvidence(),
                null,
                List.of());
    }

    private AnalysisResultResponse.InsightSummary insightSummary(
            String title, String content, InsightSeverity severity) {
        return new AnalysisResultResponse.InsightSummary(
                UUID.randomUUID(), InsightType.ARCHITECTURAL, severity,
                title, content, null, null, List.of(), UUID.randomUUID());
    }
}
