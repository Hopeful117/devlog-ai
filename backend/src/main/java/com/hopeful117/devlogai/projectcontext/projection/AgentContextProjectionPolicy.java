package com.hopeful117.devlogai.projectcontext.projection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentContextProjectionPolicy {
    public static final String POLICY_ID = "agent-context-payload";
    public static final String POLICY_VERSION = "v1";

    private final int maximumBytes;
    private final int maximumEstimatedTokens;
    private final int maximumReasonsPerEvidence;
    private final int maximumRelatedReferencesPerEvidence;

    public AgentContextProjectionPolicy(
            @Value("${devlog.engineering-story.agent-context.max-bytes:32768}")
            int maximumBytes,
            @Value("${devlog.engineering-story.agent-context.max-estimated-tokens:8192}")
            int maximumEstimatedTokens,
            @Value("${devlog.engineering-story.agent-context.max-reasons-per-evidence:3}")
            int maximumReasonsPerEvidence,
            @Value("${devlog.engineering-story.agent-context.max-related-references-per-evidence:3}")
            int maximumRelatedReferencesPerEvidence
    ) {
        if (maximumBytes < 1 || maximumEstimatedTokens < 1
                || maximumReasonsPerEvidence < 1
                || maximumRelatedReferencesPerEvidence < 0) {
            throw new IllegalArgumentException("Agent context projection limits are invalid");
        }
        this.maximumBytes = maximumBytes;
        this.maximumEstimatedTokens = maximumEstimatedTokens;
        this.maximumReasonsPerEvidence = maximumReasonsPerEvidence;
        this.maximumRelatedReferencesPerEvidence = maximumRelatedReferencesPerEvidence;
    }

    public int maximumBytes() { return maximumBytes; }
    public int maximumEstimatedTokens() { return maximumEstimatedTokens; }
    public int maximumReasonsPerEvidence() { return maximumReasonsPerEvidence; }
    public int maximumRelatedReferencesPerEvidence() {
        return maximumRelatedReferencesPerEvidence;
    }
}
