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

    public record ObjectiveUnderstanding(String summary) {
        public ObjectiveUnderstanding {
            // Empty allowed
        }
    }

    public record ArchitectureFinding(
            String title,
            String description,
            EvidenceRef evidenceRef,
            String relationType
    ) {
        public ArchitectureFinding {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (evidenceRef == null) throw new IllegalArgumentException("evidenceRef must not be null");
            if (relationType == null || relationType.isBlank()) throw new IllegalArgumentException("relationType must not be blank");
        }
    }

    public record DecisionFinding(
            String decisionId,
            String title,
            String summary,
            EvidenceRef evidenceRef,
            String relevance
    ) {
        public DecisionFinding {
            if (decisionId == null || decisionId.isBlank()) throw new IllegalArgumentException("decisionId must not be blank");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary must not be blank");
            if (evidenceRef == null) throw new IllegalArgumentException("evidenceRef must not be null");
            if (relevance == null || relevance.isBlank()) throw new IllegalArgumentException("relevance must not be blank");
        }
    }

    public record EvidenceFinding(
            String evidenceId,
            String kind,
            String summary,
            EvidenceRef evidenceRef,
            String interpretation
    ) {
        public EvidenceFinding {
            if (evidenceId == null || evidenceId.isBlank()) throw new IllegalArgumentException("evidenceId must not be blank");
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
            if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary must not be blank");
            if (evidenceRef == null) throw new IllegalArgumentException("evidenceRef must not be null");
            if (interpretation == null || interpretation.isBlank()) throw new IllegalArgumentException("interpretation must not be blank");
        }
    }

    public record HistoricalContextItem(
            String storyId,
            String storyTitle,
            String summary,
            EvidenceRef evidenceRef,
            String relevance
    ) {
        public HistoricalContextItem {
            if (storyId == null || storyId.isBlank()) throw new IllegalArgumentException("storyId must not be blank");
            if (storyTitle == null || storyTitle.isBlank()) throw new IllegalArgumentException("storyTitle must not be blank");
            if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary must not be blank");
            if (evidenceRef == null) throw new IllegalArgumentException("evidenceRef must not be null");
            if (relevance == null || relevance.isBlank()) throw new IllegalArgumentException("relevance must not be blank");
        }
    }

    public record ConstraintFinding(
            String title,
            String description,
            EvidenceRef evidenceRef,
            String constraintType
    ) {
        public ConstraintFinding {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (evidenceRef == null) throw new IllegalArgumentException("evidenceRef must not be null");
            if (constraintType == null || constraintType.isBlank()) throw new IllegalArgumentException("constraintType must not be blank");
        }
    }

    public record ImpactedComponentFinding(
            String component,
            String impactType,
            String description,
            EvidenceRef evidenceRef,
            String confidence
    ) {
        public ImpactedComponentFinding {
            if (component == null || component.isBlank()) throw new IllegalArgumentException("component must not be blank");
            if (impactType == null || impactType.isBlank()) throw new IllegalArgumentException("impactType must not be blank");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (evidenceRef == null) throw new IllegalArgumentException("evidenceRef must not be null");
            if (confidence == null || confidence.isBlank()) throw new IllegalArgumentException("confidence must not be blank");
        }
    }

    public record Uncertainty(
            String description,
            String reason
    ) {
        public Uncertainty {
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public record MissingInformation(
            String description,
            String reason
    ) {
        public MissingInformation {
            if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public record ImplementationQuestion(
            String question,
            String context
    ) {
        public ImplementationQuestion {
            if (question == null || question.isBlank()) throw new IllegalArgumentException("question must not be blank");
            if (context == null || context.isBlank()) throw new IllegalArgumentException("context must not be blank");
        }
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public record Provenance(
            String contextDigest,
            String promptVersion,
            Instant generatedAt
    ) {
        public Provenance {
            if (generatedAt == null) generatedAt = Instant.now();
        }
    }

    public record OutputClassification(
            List<ClassificationEntry> entries
    ) {
        public OutputClassification {
            entries = entries != null ? List.copyOf(entries) : List.of();
        }

        public record ClassificationEntry(
                String findingType,
                String findingId,
                Classification classification,
                boolean grounded
        ) {
            public ClassificationEntry {
                if (findingType == null || findingType.isBlank()) throw new IllegalArgumentException("findingType must not be blank");
                if (findingId == null || findingId.isBlank()) throw new IllegalArgumentException("findingId must not be blank");
                if (classification == null) throw new IllegalArgumentException("classification must not be null");
            }
        }

        public enum Classification {
            FACTUAL_EXTRACTION,
            AI_INTERPRETATION,
            RECOMMENDATION
        }
    }
}