package com.hopeful117.devlogai.contracts.storycontextanalysis;

import java.util.List;

public record StoryContextAnalysisResult(
        ObjectiveUnderstanding objectiveUnderstanding,
        List<ArchitectureFinding> architectureFindings,
        List<DecisionFinding> decisionFindings,
        List<EvidenceFinding> evidenceFindings,
        List<HistoricalContextItem> historicalContext,
        List<ConstraintFinding> constraints,
        List<ImpactedComponentFinding> impactedComponents,
        List<Uncertainty> uncertainties,
        List<MissingInformation> missingInformation,
        List<ImplementationQuestion> implementationQuestions,
        Confidence confidence,
        Provenance provenance,
        List<OutputClassification> outputClassification
) {
    public StoryContextAnalysisResult {
        objectiveUnderstanding = objectiveUnderstanding == null ? new ObjectiveUnderstanding("", List.of(), List.of(), List.of()) : objectiveUnderstanding;
        architectureFindings = architectureFindings == null ? List.of() : List.copyOf(architectureFindings);
        decisionFindings = decisionFindings == null ? List.of() : List.copyOf(decisionFindings);
        evidenceFindings = evidenceFindings == null ? List.of() : List.copyOf(evidenceFindings);
        historicalContext = historicalContext == null ? List.of() : List.copyOf(historicalContext);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        impactedComponents = impactedComponents == null ? List.of() : List.copyOf(impactedComponents);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
        implementationQuestions = implementationQuestions == null ? List.of() : List.copyOf(implementationQuestions);
        if (confidence == null) throw new IllegalArgumentException("StoryContextAnalysisResult confidence must not be null");
        if (provenance == null) throw new IllegalArgumentException("StoryContextAnalysisResult provenance must not be null");
        outputClassification = outputClassification == null ? List.of() : List.copyOf(outputClassification);
    }
}