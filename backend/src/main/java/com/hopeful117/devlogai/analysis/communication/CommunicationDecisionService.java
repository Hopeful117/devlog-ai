package com.hopeful117.devlogai.analysis.communication;

import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.analysis.result.service.AnalysisResultQueryService;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunicationDecisionService {

    private final AnalysisResultQueryService analysisResultQueryService;

    public CommunicationDecision evaluate(UUID analysisId) {
        AnalysisResultResponse result = analysisResultQueryService.getResult(analysisId);

        if (hasCommunicableSynthesis(result)) {
            log.debug("Analysis {} has communicable synthesis; decision=SPEAK", analysisId);
            return CommunicationDecision.SPEAK;
        }

        if (hasCommunicableInsight(result)) {
            log.debug("Analysis {} has communicable insight; decision=SPEAK", analysisId);
            return CommunicationDecision.SPEAK;
        }

        log.debug("Analysis {} has no communicable content; decision=SILENCE", analysisId);
        return CommunicationDecision.SILENCE;
    }

    private boolean hasCommunicableSynthesis(AnalysisResultResponse result) {
        AnalysisResultResponse.SynthesisSection synthesis = result.synthesis();
        if (synthesis == null) {
            return false;
        }
        if (synthesis.items() == null || synthesis.items().isEmpty()) {
            return false;
        }
        return synthesis.items().stream()
                .anyMatch(item -> isNotBlank(item.name()) && isNotBlank(item.content()));
    }

    private boolean hasCommunicableInsight(AnalysisResultResponse result) {
        AnalysisResultResponse.InsightsSection insights = result.insights();
        if (insights == null || insights.items() == null || insights.items().isEmpty()) {
            return false;
        }
        return insights.items().stream()
                .filter(i -> isNotBlank(i.title()) && isNotBlank(i.content()))
                .max(Comparator
                        .comparing(AnalysisResultResponse.InsightSummary::severity,
                                Comparator.comparing(InsightSeverity::ordinal))
                        .thenComparing(AnalysisResultResponse.InsightSummary::id))
                .isPresent();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
