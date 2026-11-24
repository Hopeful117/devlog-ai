package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
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
        Map<String, String> resourceByReference = groundingResources(groundingContract);
        String expectedDigest = task.getContextDigest();
        String actualDigest = result.provenance().contextDigest();
        if (expectedDigest != null && !expectedDigest.equals(actualDigest)) {
            throw new IllegalStateException("Context digest mismatch: expected " + expectedDigest + ", got " + actualDigest);
        }

        boolean hasFindings = !result.architectureFindings().isEmpty()
                || !result.decisionFindings().isEmpty()
                || !result.evidenceFindings().isEmpty()
                || !result.historicalContext().isEmpty()
                || !result.constraintFindings().isEmpty()
                || !result.impactedComponentFindings().isEmpty();
        boolean hasObjective = result.objectiveUnderstanding() != null
                && !result.objectiveUnderstanding().summary().isBlank();
        boolean hasGrounding = result.objectiveUnderstanding() != null
                && result.objectiveUnderstanding().grounding() != null;
        if (!hasFindings && !hasObjective && !hasGrounding) {
            throw new IllegalStateException(
                    "Analysis result is fully empty: no findings, no objective summary, "
                    + "and no grounding metadata. "
                    + "The AI engine could not produce a substantive response.");
        }

        validateObjectiveUnderstanding(result.objectiveUnderstanding(), allowedRefSet);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.architectureFindings(), allowedRefSet, true);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.decisionFindings(), allowedRefSet, true);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.evidenceFindings(), allowedRefSet, false);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.historicalContext(), allowedRefSet, true);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.constraintFindings(), allowedRefSet, false);
        AnalyzeStoryContextUseCase.validateGroundedFindings(result.impactedComponentFindings(), allowedRefSet, true);
        validateImplementationPreparation(result.implementationPreparation(), allowedRefSet, resourceByReference);

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
        validateOutputClassification(result);
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

    private static void validateOutputClassification(StoryContextAnalysisResult result) {
        Map<String, StoryContextAnalysisResult.GroundingMetadata> groundingByReference = new LinkedHashMap<>();
        if (result.objectiveUnderstanding() != null
                && result.objectiveUnderstanding().grounding() != null) {
            groundingByReference.put("objectiveUnderstanding", result.objectiveUnderstanding().grounding());
        }
        addGroundings(groundingByReference, "architectureFindings", result.architectureFindings());
        addGroundings(groundingByReference, "decisionFindings", result.decisionFindings());
        addGroundings(groundingByReference, "evidenceFindings", result.evidenceFindings());
        addGroundings(groundingByReference, "historicalContext", result.historicalContext());
        addGroundings(groundingByReference, "constraintFindings", result.constraintFindings());
        addGroundings(groundingByReference, "impactedComponentFindings", result.impactedComponentFindings());

        Set<String> classifiedReferences = new HashSet<>();
        for (StoryContextAnalysisResult.OutputClassification.ClassificationEntry entry
                : result.outputClassification().entries()) {
            if (!classifiedReferences.add(entry.findingReference())) {
                throw new IllegalStateException(
                        "Output classification contains duplicate item: " + entry.findingReference());
            }
            StoryContextAnalysisResult.GroundingMetadata grounding = groundingByReference.get(entry.findingReference());
            if (grounding == null) {
                throw new IllegalStateException(
                        "Output classification references unknown item: " + entry.findingReference());
            }
            if (!entry.classification().name().equals(grounding.classification())
                    || entry.grounded() != grounding.grounded()) {
                throw new IllegalStateException(
                        "Output classification disagrees with grounding: " + entry.findingReference());
            }
        }
        if (!classifiedReferences.containsAll(groundingByReference.keySet())) {
            Set<String> missing = new HashSet<>(groundingByReference.keySet());
            missing.removeAll(classifiedReferences);
            throw new IllegalStateException("Output classification omits items: " + missing);
        }
    }

    private static void addGroundings(
            Map<String, StoryContextAnalysisResult.GroundingMetadata> target,
            String field,
            java.util.List<? extends Object> findings) {
        for (int index = 0; index < findings.size(); index++) {
            Object finding = findings.get(index);
            try {
                Object grounding = finding.getClass().getMethod("grounding").invoke(finding);
                target.put(field + "[" + index + "]",
                        (StoryContextAnalysisResult.GroundingMetadata) grounding);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to inspect output classification target", exception);
            }
        }
    }

    static void validateObjectiveUnderstanding(
            StoryContextAnalysisResult.ObjectiveUnderstanding objective,
            Set<String> allowedRefSet) {
        if (objective == null) {
            throw new IllegalStateException("Objective understanding is required");
        }
        if (objective.grounding() == null) {
            if (!objective.summary().isBlank()) {
                throw new IllegalStateException(
                        "Objective understanding summary requires grounding metadata");
            }
            return;
        }
        String classification = objective.grounding().classification();
        if (!Set.of("FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION")
                .contains(classification)) {
            throw new IllegalStateException("Invalid objective understanding classification: " + classification);
        }
        for (EvidenceRef evidenceRef : objective.grounding().evidenceReferences()) {
            if (!allowedRefSet.contains(evidenceRef.reference())) {
                throw new IllegalStateException(
                        "Objective understanding references unauthorized evidence: " + evidenceRef.reference());
            }
        }
        if (objective.summary().isBlank()) return;
        boolean requiresEvidence = Set.of("FACTUAL_EXTRACTION", "AI_INTERPRETATION")
                .contains(classification);
        if (requiresEvidence && (!objective.grounding().grounded()
                || objective.grounding().evidenceReferences().isEmpty())) {
            throw new IllegalStateException(
                    "Objective understanding factual or interpretative summary must be grounded");
        }
        if (objective.grounding().grounded()
                && objective.grounding().evidenceReferences().isEmpty()) {
            throw new IllegalStateException(
                    "Grounded objective understanding requires evidence references");
        }
    }

    static void validateImplementationPreparation(
            StoryContextAnalysisResult.ImplementationPreparation preparation,
            Set<String> allowedRefSet) {
        validateImplementationPreparation(preparation, allowedRefSet, Map.of());
    }

    static void validateImplementationPreparation(
            StoryContextAnalysisResult.ImplementationPreparation preparation,
            Set<String> allowedRefSet,
            Map<String, String> resourceByReference) {
        for (StoryContextAnalysisResult.ImplementationFile item : preparation.affectedFiles()) {
            validatePreparationEvidence(item.evidenceReferences(), allowedRefSet, "affected file " + item.path());
            boolean supported = item.evidenceReferences().stream().anyMatch(reference ->
                    item.path().equals(reference.reference())
                            || item.path().equals(reference.resource())
                            || (reference.resource() != null && reference.resource().endsWith("/" + item.path()))
                            || item.path().equals(resourceByReference.get(reference.reference()))
                            || (resourceByReference.get(reference.reference()) != null
                            && resourceByReference.get(reference.reference()).endsWith("/" + item.path())));
            if (!supported) {
                throw new IllegalStateException("Affected file is not supported by cited evidence: " + item.path());
            }
        }
        for (StoryContextAnalysisResult.ImplementationConstraint item : preparation.constraints()) {
            validatePreparationEvidence(item.evidenceReferences(), allowedRefSet, "constraint");
        }
        for (StoryContextAnalysisResult.ImplementationTestPlanItem item : preparation.testPlan()) {
            validatePreparationEvidence(item.evidenceReferences(), allowedRefSet, "test plan " + item.target());
        }
    }

    private static Map<String, String> groundingResources(Map<String, Object> groundingContract) {
        Map<String, String> resources = new LinkedHashMap<>();
        Object raw = groundingContract.get("allowedGroundingReferences");
        if (!(raw instanceof Iterable<?> entries)) return resources;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Object reference = map.get("ref");
            Object resource = map.get("resource");
            if (reference instanceof String ref && resource instanceof String path && !path.isBlank()) {
                resources.put(ref, path);
            }
        }
        return resources;
    }

    private static void validatePreparationEvidence(
            java.util.List<EvidenceRef> references,
            Set<String> allowedRefSet,
            String itemDescription) {
        if (references.isEmpty()) {
            throw new IllegalStateException("Implementation preparation item is ungrounded: " + itemDescription);
        }
        for (EvidenceRef evidenceRef : references) {
            if (!allowedRefSet.contains(evidenceRef.reference())) {
                throw new IllegalStateException(
                        "Implementation preparation references unauthorized evidence: " + evidenceRef.reference());
            }
        }
    }
}
