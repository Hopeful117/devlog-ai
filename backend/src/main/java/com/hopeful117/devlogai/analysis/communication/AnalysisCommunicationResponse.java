package com.hopeful117.devlogai.analysis.communication;

import java.util.UUID;

public record AnalysisCommunicationResponse(
        UUID analysisId,
        String status,
        String agent,
        String intent,
        String title,
        String body
) {
    public static AnalysisCommunicationResponse communicated(
            UUID analysisId, String agent, String intent, String title, String body) {
        return new AnalysisCommunicationResponse(analysisId, "COMMUNICATED", agent, intent, title, body);
    }

    public static AnalysisCommunicationResponse silence(UUID analysisId) {
        return new AnalysisCommunicationResponse(analysisId, "SILENCE", null, null, null, null);
    }
}
