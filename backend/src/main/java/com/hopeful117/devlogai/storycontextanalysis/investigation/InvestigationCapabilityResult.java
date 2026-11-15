package com.hopeful117.devlogai.storycontextanalysis.investigation;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;

import java.util.List;
import java.util.UUID;

public record InvestigationCapabilityResult(
        UUID projectId,
        UUID storyId,
        String actionId,
        InvestigationActionKind kind,
        String projectSlug,
        String revision,
        Outcome outcome,
        List<Evidence> evidence,
        String rejectionCode,
        String rejectionMessage
) {
    public InvestigationCapabilityResult {
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }

    public enum Outcome {
        RESULTS,
        EMPTY,
        REJECTED,
        UNAVAILABLE
    }

    public record Evidence(
            String reference,
            String kind,
            String path,
            String revision,
            String sourceType,
            String identifier,
            String summary,
            TrustTier trustTier,
            boolean citable,
            String content,
            Integer startLine,
            Integer endLine
    ) { }

    public static InvestigationCapabilityResult rejected(
            UUID projectId, UUID storyId, InvestigationAction action,
            String code, String message) {
        return new InvestigationCapabilityResult(projectId, storyId, action == null ? null : action.actionId(),
                action == null ? null : action.kind(), action == null ? null : action.projectSlug(),
                action == null ? null : action.revision(), Outcome.REJECTED, List.of(), code, message);
    }
}
