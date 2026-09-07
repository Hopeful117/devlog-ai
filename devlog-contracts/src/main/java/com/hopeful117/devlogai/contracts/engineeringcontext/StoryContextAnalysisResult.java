package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.time.Instant;
import java.util.List;

public record StoryContextAnalysisResult(
        ObjectiveUnderstanding objectiveUnderstanding,
        List<ArchitectureFinding> architectureFindings,
        List<DecisionFinding> decisionFindings,
        List<EvidenceFinding> evidenceFindings,
        List<HistoricalContextItem> historicalContext,
        List<ConstraintFinding> constraintFindings,
        List<ImpactedComponentFinding> impactedComponentFindings,
        List<Uncertainty> uncertainties,
        List<MissingInformation> missingInformation,
        List<ImplementationQuestion> implementationQuestions,
        Confidence confidence,
        Provenance provenance,
        OutputClassification outputClassification
) {
    public StoryContextAnalysisResult {
        objectiveUnderstanding = objectiveUnderstanding != null ? objectiveUnderstanding : new ObjectiveUnderstanding(null);
        architectureFindings = architectureFindings != null ? List.copyOf(architectureFindings) : List.of();
        decisionFindings = decisionFindings != null ? List.copyOf(decisionFindings) : List.of();
        evidenceFindings = evidenceFindings != null ? List.copyOf(evidenceFindings) : List.of();
        historicalContext = historicalContext != null ? List.copyOf(historicalContext) : List.of();
        constraintFindings = constraintFindings != null ? List.copyOf(constraintFindings) : List.of();
        impactedComponentFindings = impactedComponentFindings != null ? List.copyOf(impactedComponentFindings) : List.of();
        uncertainties = uncertainties != null ? List.copyOf(uncertainties) : List.of();
        missingInformation = missingInformation != null ? List.copyOf(missingInformation) : List.of();
        implementationQuestions = implementationQuestions != null ? List.copyOf(implementationQuestions) : List.of();
        confidence = confidence != null ? confidence : Confidence.LOW;
        provenance = provenance != null ? provenance : new Provenance(null, null, null);
        outputClassification = outputClassification != null ? outputClassification : new OutputClassification(List.of());
    }

    public record ObjectiveUnderstanding(
            String summary,
            List<String> keyDomains,
            List<String> primaryTechnologies,
            List<String> architecturalPatterns
    ) {
        public ObjectiveUnderstanding {
            summary = summary != null ? summary : "";
            keyDomains = keyDomains != null ? List.copyOf(keyDomains) : List.of();
            primaryTechnologies = primaryTechnologies != null ? List.copyOf(primaryTechnologies) : List.of();
            architecturalPatterns = architecturalPatterns != null ? List.copyOf(architecturalPatterns) : List.of();
        }
        
        // Backward compatibility constructor for compact constructor
        public ObjectiveUnderstanding(String summary) {
            this(summary, List.of(), List.of(), List.of());
        }
    }

    public record ArchitectureFinding(
            String title,
            String description,
            GroundingMetadata grounding
    ) {
        public ArchitectureFinding {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (grounding == null) throw new IllegalArgumentException("grounding must not be null");
        }
    }

    public record DecisionFinding(
            String title,
            String context,
            String choice,
            String rationale,
            GroundingMetadata grounding
    ) {
        public DecisionFinding {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (context == null || context.isBlank()) throw new IllegalArgumentException("context must not be blank");
            if (choice == null || choice.isBlank()) throw new IllegalArgumentException("choice must not be blank");
            if (rationale == null || rationale.isBlank()) throw new IllegalArgumentException("rationale must not be blank");
            if (grounding == null) throw new IllegalArgumentException("grounding must not be null");
        }
    }

    public record EvidenceFinding(
            String title,
            String description,
            String evidenceKind,
            GroundingMetadata grounding
    ) {
        public EvidenceFinding {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (evidenceKind == null || evidenceKind.isBlank()) throw new IllegalArgumentException("evidenceKind must not be blank");
            if (grounding == null) throw new IllegalArgumentException("grounding must not be null");
        }
    }

    public record HistoricalContextItem(
            String title,
            String description,
            String period,
            List<String> relatedCommitHashes,
            GroundingMetadata grounding
    ) {
        public HistoricalContextItem {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (period == null || period.isBlank()) throw new IllegalArgumentException("period must not be blank");
            relatedCommitHashes = relatedCommitHashes != null ? List.copyOf(relatedCommitHashes) : List.of();
            if (grounding == null) throw new IllegalArgumentException("grounding must not be null");
        }
    }

    public record ConstraintFinding(
            String title,
            String description,
            String constraintType,
            GroundingMetadata grounding
    ) {
        public ConstraintFinding {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (constraintType == null || constraintType.isBlank()) throw new IllegalArgumentException("constraintType must not be blank");
            if (grounding == null) throw new IllegalArgumentException("grounding must not be null");
        }
    }

    public record ImpactedComponentFinding(
            String componentName,
            String impactDescription,
            String impactType,
            GroundingMetadata grounding
    ) {
        public ImpactedComponentFinding {
            if (componentName == null || componentName.isBlank()) throw new IllegalArgumentException("componentName must not be blank");
            if (impactDescription == null || impactDescription.isBlank()) throw new IllegalArgumentException("impactDescription must not be blank");
            if (impactType == null || impactType.isBlank()) throw new IllegalArgumentException("impactType must not be blank");
            if (grounding == null) throw new IllegalArgumentException("grounding must not be null");
        }
    }

    public record Uncertainty(
            String description,
            String reason,
            List<EvidenceRef> relatedEvidence
    ) {
        public Uncertainty {
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
            relatedEvidence = relatedEvidence != null ? List.copyOf(relatedEvidence) : List.of();
        }
    }

    public record MissingInformation(
            String description,
            String reason,
            List<String> suggestedSources
    ) {
        public MissingInformation {
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
            suggestedSources = suggestedSources != null ? List.copyOf(suggestedSources) : List.of();
        }
    }

    public record ImplementationQuestion(
            String question,
            String context,
            List<String> relatedComponents
    ) {
        public ImplementationQuestion {
            if (question == null || question.isBlank()) throw new IllegalArgumentException("question must not be blank");
            if (context == null || context.isBlank()) throw new IllegalArgumentException("context must not be blank");
            relatedComponents = relatedComponents != null ? List.copyOf(relatedComponents) : List.of();
        }
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public enum RelationType {
        EXPLICIT,
        TEMPORAL_PROXIMITY,
        POSSIBLE_RELEVANCE,
        INFERRED_HYPOTHESIS
    }

    public record GroundingMetadata(
            List<EvidenceRef> evidenceReferences,
            String classification,
            boolean grounded,
            RelationType relationType
    ) {
        public GroundingMetadata {
            evidenceReferences = evidenceReferences != null ? List.copyOf(evidenceReferences) : List.of();
            if (classification == null || classification.isBlank()) throw new IllegalArgumentException("classification must not be blank");
            if (relationType == null) throw new IllegalArgumentException("relationType must not be null");
        }
    }

    public record Provenance(
            String contextDigest,
            String promptVersion,
            String provider,
            String modelIdentifier,
            String promptContentDigest,
            String intentId,
            String intentVersion,
            List<String> guidanceKeys,
            java.util.Map<String, Object> executionMetadata
    ) {
        public Provenance {
            guidanceKeys = guidanceKeys != null ? List.copyOf(guidanceKeys) : List.of();
            executionMetadata = executionMetadata != null ? java.util.Map.copyOf(executionMetadata) : java.util.Map.of();
        }
        
        // Backward compatibility constructor for compact constructor
        public Provenance(String contextDigest, String promptVersion, Instant generatedAt) {
            this(contextDigest, promptVersion, null, null, null, null, null, List.of(), java.util.Map.of());
        }
    }

    public record OutputClassification(
            List<ClassificationEntry> entries
    ) {
        public OutputClassification {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }

        public record ClassificationEntry(
                String findingReference,
                Classification classification,
                boolean grounded,
                String rationale
        ) {
            public ClassificationEntry {
                if (findingReference == null || findingReference.isBlank()) throw new IllegalArgumentException("findingReference must not be blank");
                if (classification == null) throw new IllegalArgumentException("classification must not be null");
                rationale = rationale != null ? rationale : "";
            }
        }

        public enum Classification {
            FACTUAL_EXTRACTION,
            AI_INTERPRETATION,
            RECOMMENDATION
        }
    }
}