package com.hopeful117.devlogai.storycontextanalysis.investigation;

import java.util.Map;

public final class InvestigationCapabilityRegistry {
    private InvestigationCapabilityRegistry() { }

    public static Map<InvestigationActionKind, InvestigationCapability> phaseOne() {
        return Map.of(
                InvestigationActionKind.SEARCH_EVIDENCE,
                new InvestigationCapability(InvestigationActionKind.SEARCH_EVIDENCE, 20, 64_000, 8_000),
                InvestigationActionKind.READ_EVIDENCE_RANGE,
                new InvestigationCapability(InvestigationActionKind.READ_EVIDENCE_RANGE, 1, 64_000, 8_000));
    }
}
