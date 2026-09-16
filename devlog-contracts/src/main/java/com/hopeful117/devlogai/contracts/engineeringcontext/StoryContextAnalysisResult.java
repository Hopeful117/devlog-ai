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
        OutputClassification outputClassification,
        List<CausalClaim> causalClaims,
        CausalAssessment causalAssessment
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
        causalClaims = causalClaims != null ? List.copyOf(causalClaims) : List.of();
    }

    /** Backward-compatible constructor for payloads produced before causalClaims. */
    public StoryContextAnalysisResult(
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
        this(objectiveUnderstanding, architectureFindings, decisionFindings,
                evidenceFindings, historicalContext, constraintFindings,
                impactedComponentFindings, uncertainties, missingInformation,
                implementationQuestions, confidence, provenance,
                outputClassification, List.of(), null);
    }

    /** Legacy causal-claim constructor retained for historical payloads. */
    public StoryContextAnalysisResult(
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
            OutputClassification outputClassification,
            List<CausalClaim> causalClaims
    ) {
        this(objectiveUnderstanding, architectureFindings, decisionFindings,
                evidenceFindings, historicalContext, constraintFindings,
                impactedComponentFindings, uncertainties, missingInformation,
                implementationQuestions, confidence, provenance,
                outputClassification, causalClaims, null);
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

    public record CausalClaim(
            String source,
            String target,
            CausalClassification causalClassification,
            CausalEvidenceBasis evidenceBasis,
            List<EvidenceRef> evidenceReferences,
            String explanation
    ) {
        public CausalClaim {
            if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
            if (target == null || target.isBlank()) throw new IllegalArgumentException("target must not be blank");
            if (source.equals(target)) throw new IllegalArgumentException("source and target must differ");
            if (causalClassification == null) throw new IllegalArgumentException("causalClassification must not be null");
            if (evidenceBasis == null) throw new IllegalArgumentException("evidenceBasis must not be null");
            evidenceReferences = evidenceReferences != null ? List.copyOf(evidenceReferences) : List.of();
            if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("explanation must not be blank");
            if (evidenceReferences.stream().anyMatch(reference -> reference == null || reference.role() == null)) {
                throw new IllegalArgumentException("causal evidenceReferences require a role");
            }
            if (causalClassification != CausalClassification.NOT_ESTABLISHED && evidenceReferences.isEmpty()) {
                throw new IllegalArgumentException("affirmative causal claims require evidenceReferences");
            }
            if (causalClassification == CausalClassification.STRONGLY_SUPPORTED
                    && evidenceReferences.stream()
                    .filter(reference -> reference.role() == EvidenceRef.CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT)
                    .map(EvidenceRef::reference).distinct().count() < 2) {
                throw new IllegalArgumentException("STRONGLY_SUPPORTED requires multiple distinct MATERIAL_RELATIONSHIP_SUPPORT references");
            }
            boolean affirmativeBasis = evidenceBasis == CausalEvidenceBasis.DIRECT_DOCUMENTATION
                    || evidenceBasis == CausalEvidenceBasis.MATERIAL_CORROBORATION;
            if (causalClassification == CausalClassification.NOT_ESTABLISHED && affirmativeBasis) {
                throw new IllegalArgumentException("NOT_ESTABLISHED requires non-affirmative evidenceBasis");
            }
            if (causalClassification == CausalClassification.EXPLICITLY_DOCUMENTED
                    && evidenceBasis != CausalEvidenceBasis.DIRECT_DOCUMENTATION) {
                throw new IllegalArgumentException("EXPLICITLY_DOCUMENTED requires DIRECT_DOCUMENTATION");
            }
            if (causalClassification == CausalClassification.STRONGLY_SUPPORTED
                    && evidenceBasis != CausalEvidenceBasis.MATERIAL_CORROBORATION) {
                throw new IllegalArgumentException("STRONGLY_SUPPORTED requires MATERIAL_CORROBORATION");
            }
            boolean contradictory = evidenceReferences.stream().anyMatch(reference ->
                    reference.role() == EvidenceRef.CausalEvidenceRole.CONTRADICTORY_EVIDENCE);
            boolean nonCausalContext = evidenceReferences.stream().anyMatch(reference ->
                    reference.role() == EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT);
            boolean direct = evidenceReferences.stream().anyMatch(reference ->
                    reference.role() == EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT);
            boolean material = evidenceReferences.stream()
                    .filter(reference -> reference.role() == EvidenceRef.CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT)
                    .map(EvidenceRef::reference).distinct().count() >= 2;
            if (causalClassification == CausalClassification.EXPLICITLY_DOCUMENTED
                    && (!direct || contradictory || nonCausalContext)) {
                throw new IllegalArgumentException("EXPLICITLY_DOCUMENTED requires direct relationship evidence without context or contradiction");
            }
            if (causalClassification == CausalClassification.STRONGLY_SUPPORTED
                    && (!material || contradictory || nonCausalContext)) {
                throw new IllegalArgumentException("STRONGLY_SUPPORTED exceeds the defensible evidence level");
            }
        }
    }

    /** Core-owned question whose identity cannot be changed by the model. */
    public record CausalQuestion(
            String source,
            String target,
            String relationAsked,
            boolean answerRequired
    ) {
        public CausalQuestion {
            if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
            if (target == null || target.isBlank()) throw new IllegalArgumentException("target must not be blank");
            if (source.equals(target)) throw new IllegalArgumentException("source and target must differ");
            if (relationAsked == null || relationAsked.isBlank()) {
                throw new IllegalArgumentException("relationAsked must not be blank");
            }
        }
    }

    public record EvidenceLocator(
            LocatorKind kind,
            Integer startLine,
            Integer endLine,
            String heading
    ) {
        public EvidenceLocator {
            if (kind == null) throw new IllegalArgumentException("locator kind must not be null");
            switch (kind) {
                case LINE_RANGE -> {
                    if (startLine == null || endLine == null || startLine < 1 || endLine < startLine) {
                        throw new IllegalArgumentException("line locator requires a positive ordered range");
                    }
                }
                case SECTION -> {
                    if (heading == null || heading.isBlank()) {
                        throw new IllegalArgumentException("section locator requires a heading");
                    }
                }
            }
        }

        public enum LocatorKind {
            LINE_RANGE,
            SECTION
        }
    }

    /** Content-level support over an existing authorized EvidenceRef identity. */
    public record EvidenceAssertion(
            EvidenceRef evidenceReference,
            EvidenceLocator locator,
            String resolvedContentDigest,
            String resolvedContent,
            String excerpt,
            EvidenceRef.CausalEvidenceRole assertionRole
    ) {
        public EvidenceAssertion {
            if (evidenceReference == null) throw new IllegalArgumentException("evidenceReference must not be null");
            if (locator == null) throw new IllegalArgumentException("locator must not be null");
            if (assertionRole == null) throw new IllegalArgumentException("assertionRole must not be null");
            if (resolvedContentDigest != null && resolvedContentDigest.isBlank()) {
                throw new IllegalArgumentException("resolvedContentDigest must not be blank");
            }
        }

        public EvidenceAssertion withResolvedContent(String content, String digest) {
            return new EvidenceAssertion(evidenceReference, locator, digest, content, excerpt, assertionRole);
        }
    }

    public record CausalAssessment(
            CausalQuestion question,
            CausalClassification classification,
            List<EvidenceAssertion> evidenceAssertions,
            String explanation
    ) {
        public CausalAssessment {
            if (question == null) throw new IllegalArgumentException("question must not be null");
            if (classification == null) throw new IllegalArgumentException("classification must not be null");
            evidenceAssertions = evidenceAssertions != null ? List.copyOf(evidenceAssertions) : List.of();
            if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("explanation must not be blank");
        }
    }

    public enum CausalClassification {
        EXPLICITLY_DOCUMENTED,
        STRONGLY_SUPPORTED,
        NOT_ESTABLISHED
    }

    public enum CausalEvidenceBasis {
        DIRECT_DOCUMENTATION,
        MATERIAL_CORROBORATION,
        CHRONOLOGY_ONLY,
        TEMPORAL_PROXIMITY_ONLY,
        SHARED_TOPIC_ONLY,
        ARCHITECTURAL_COMPATIBILITY_ONLY,
        POSSIBLE_RELEVANCE_ONLY,
        CONFLICTING,
        INSUFFICIENT
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
