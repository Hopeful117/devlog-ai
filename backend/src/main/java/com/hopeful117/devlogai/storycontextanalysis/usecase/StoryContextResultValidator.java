package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;

import java.util.Map;
import java.util.Set;

/** Coordinates authoritative validation of the structured Story Context result. */
public final class StoryContextResultValidator {
    private StoryContextResultValidator() { }

    public static StoryContextAnalysisResult validate(StoryContextAnalysisResult result, AiTask task) {
        @SuppressWarnings("unchecked")
        Map<String, Object> groundingContract = task.getContextSnapshot() != null
                ? (Map<String, Object>) task.getContextSnapshot().get("groundingContract")
                : Map.of();

        Set<String> allowedRefSet = AnalyzeStoryContextUseCase.allowedGroundingReferences(groundingContract, task);
        String expectedDigest = task.getContextDigest();
        String actualDigest = result.provenance().contextDigest();
        if (expectedDigest != null && !expectedDigest.equals(actualDigest)) {
            throw new IllegalStateException("Context digest mismatch: expected " + expectedDigest + ", got " + actualDigest);
        }

        AnalyzeStoryContextUseCase.validateGroundedFindings(result.architectureFindings(), allowedRefSet, true);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.decisionFindings(), allowedRefSet, true);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.evidenceFindings(), allowedRefSet, false);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.historicalContext(), allowedRefSet, true);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.constraintFindings(), allowedRefSet, false);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.impactedComponentFindings(), allowedRefSet, true);

        if ("V2".equals(groundingContract.get("causalContractVersion"))) {
            result = AnalyzeStoryContextUseCase.validateV2CausalAssessment(result, task, groundingContract);
        } else {
            AnalyzeStoryContextUseCase.validateCausalClaims(result.causalClaims(), allowedRefSet, groundingContract);
        }

        for (StoryContextAnalysisResult.Uncertainty uncertainty : result.uncertainties()) {
            for (EvidenceRef evidenceRef : uncertainty.relatedEvidence()) {
                if (!allowedRefSet.contains(evidenceRef.reference())) {
                    throw new IllegalStateException(
                            "Uncertainty references unauthorized evidence: " + evidenceRef.reference());
                }
            }
        }
        for (StoryContextAnalysisResult.OutputClassification.ClassificationEntry entry
                : result.outputClassification().entries()) {
            if (!Set.of("FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION")
                    .contains(entry.classification().name())) {
                throw new IllegalStateException("Invalid output classification: " + entry.classification());
            }
        }
        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(result.confidence().name())) {
            throw new IllegalStateException("Invalid confidence level: " + result.confidence());
        }
        return result;
    }
}
