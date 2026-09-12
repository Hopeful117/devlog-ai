package com.hopeful117.devlogai.analysis.communication;

import com.hopeful117.devlogai.agentcommunication.AgentCommunication;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationIntent;
import com.hopeful117.devlogai.agentcommunication.AgentCommunicationPort;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.analysis.result.service.AnalysisResultQueryService;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisCommunicationUseCase {

    private static final String AGENT = "DEVLOG";
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_BODY_LENGTH = 200;

    private final AnalysisService analysisService;
    private final AnalysisResultQueryService analysisResultQueryService;
    private final AgentCommunicationPort agentCommunicationPort;

    public AnalysisCommunicationResponse execute(UUID analysisId) {
        AnalysisResponse analysis = analysisService.getById(analysisId);

        if (analysis.status() != AnalysisStatus.COMPLETED) {
            log.debug("Analysis {} is not completed (status={}); skipping communication",
                    analysisId, analysis.status());
            return AnalysisCommunicationResponse.silence(analysisId);
        }

        AnalysisResultResponse result = analysisResultQueryService.getResult(analysisId);

        AgentCommunication communication = compose(result);

        if (communication == null) {
            log.debug("No meaningful semantic content for analysis {}; silence", analysisId);
            return AnalysisCommunicationResponse.silence(analysisId);
        }

        agentCommunicationPort.communicate(communication);

        log.info("Communicated analysis {} : agent={} intent={} title='{}'",
                analysisId, communication.agent(), communication.intent(), communication.title());

        return AnalysisCommunicationResponse.communicated(
                analysisId,
                communication.agent(),
                communication.intent().name(),
                communication.title(),
                communication.body());
    }

    AgentCommunication compose(AnalysisResultResponse result) {
        AgentCommunication fromSynthesis = composeFromSynthesis(result);
        if (fromSynthesis != null) {
            return fromSynthesis;
        }

        return composeFromInsight(result);
    }

    private AgentCommunication composeFromSynthesis(AnalysisResultResponse result) {
        AnalysisResultResponse.SynthesisSection synthesis = result.synthesis();
        if (synthesis == null) {
            return null;
        }

        List<AnalysisResultResponse.SynthesisItem> items = synthesis.items();
        if (items == null || items.isEmpty()) {
            return null;
        }

        AnalysisResultResponse.SynthesisItem firstItem = items.stream()
                .filter(item -> isNotBlank(item.name()) && isNotBlank(item.content()))
                .findFirst()
                .orElse(null);

        if (firstItem == null) {
            return null;
        }

        String title = bounded(synthesis.title(), MAX_TITLE_LENGTH);
        String body = bounded(firstItem.content(), MAX_BODY_LENGTH);

        return new AgentCommunication(AGENT, AgentCommunicationIntent.INFORM, title, body);
    }

    private AgentCommunication composeFromInsight(AnalysisResultResponse result) {
        AnalysisResultResponse.InsightsSection insights = result.insights();
        if (insights == null || insights.items() == null || insights.items().isEmpty()) {
            return null;
        }

        AnalysisResultResponse.InsightSummary insight = insights.items().stream()
                .filter(i -> isNotBlank(i.title()) && isNotBlank(i.content()))
                .max(Comparator
                        .comparing(AnalysisResultResponse.InsightSummary::severity,
                                Comparator.comparing(InsightSeverity::ordinal))
                        .thenComparing(AnalysisResultResponse.InsightSummary::id))
                .orElse(null);

        if (insight == null) {
            return null;
        }

        String title = bounded(insight.title(), MAX_TITLE_LENGTH);
        String body = bounded(insight.content(), MAX_BODY_LENGTH);

        return new AgentCommunication(AGENT, AgentCommunicationIntent.INFORM, title, body);
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
