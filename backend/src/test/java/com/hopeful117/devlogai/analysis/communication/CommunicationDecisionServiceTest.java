package com.hopeful117.devlogai.analysis.communication;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.analysis.result.service.AnalysisResultQueryService;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunicationDecisionServiceTest {

    @Mock
    private AnalysisResultQueryService analysisResultQueryService;

    private CommunicationDecisionService service() {
        return new CommunicationDecisionService(analysisResultQueryService);
    }

    @Test
    void speakWhenSynthesisPresent() {
        UUID analysisId = UUID.randomUUID();
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithSynthesis("Architecture Overview", "Section content"));

        CommunicationDecision decision = service().evaluate(analysisId);

        assertEquals(CommunicationDecision.SPEAK, decision);
    }

    @Test
    void silenceWhenNoSynthesisAndNoInsights() {
        UUID analysisId = UUID.randomUUID();
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(resultEmpty());

        CommunicationDecision decision = service().evaluate(analysisId);

        assertEquals(CommunicationDecision.SILENCE, decision);
    }

    @Test
    void speakWhenInsightPresent() {
        UUID analysisId = UUID.randomUUID();
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithInsights(List.of(
                        insightSummary("Critical finding", "Critical detail", InsightSeverity.CRITICAL))));

        CommunicationDecision decision = service().evaluate(analysisId);

        assertEquals(CommunicationDecision.SPEAK, decision);
    }

    @Test
    void silenceWhenSynthesisItemsEmpty() {
        UUID analysisId = UUID.randomUUID();
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithSynthesis("Title", ""));

        CommunicationDecision decision = service().evaluate(analysisId);

        assertEquals(CommunicationDecision.SILENCE, decision);
    }

    @Test
    void silenceWhenInsightsHaveBlankContent() {
        UUID analysisId = UUID.randomUUID();
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithInsights(List.of(
                        insightSummary("Title", "", InsightSeverity.CRITICAL))));

        CommunicationDecision decision = service().evaluate(analysisId);

        assertEquals(CommunicationDecision.SILENCE, decision);
    }

    @Test
    void speakWhenMultipleInsightsExist() {
        UUID analysisId = UUID.randomUUID();
        when(analysisResultQueryService.getResult(analysisId)).thenReturn(
                resultWithInsights(List.of(
                        insightSummary("Info insight", "Info detail", InsightSeverity.INFO),
                        insightSummary("Warning insight", "Warning detail", InsightSeverity.WARNING),
                        insightSummary("Critical insight", "Critical detail", InsightSeverity.CRITICAL))));

        CommunicationDecision decision = service().evaluate(analysisId);

        assertEquals(CommunicationDecision.SPEAK, decision);
    }

    // --- helpers ---

    private AnalysisResultResponse resultWithSynthesis(String title, String sectionContent) {
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
                        "NO_MATERIAL_DELTA",
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
