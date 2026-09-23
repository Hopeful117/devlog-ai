package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistry;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistryFactory;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolutionException;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.EngineeringRelationship;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SelectedKnowledgePromptProjectionService {
    static final int MAX_RELATIONSHIP_HIGHLIGHTS = 20;

    private final ObjectMapper objectMapper;
    private final SemanticSectionComposer semanticSectionComposer;

    public SelectedKnowledgePromptProjectionService(ObjectMapper objectMapper,
            SemanticSectionComposer semanticSectionComposer) {
        this.objectMapper = objectMapper;
        this.semanticSectionComposer = semanticSectionComposer;
    }

    public Map<String, Object> toMap(SelectedKnowledge selectedKnowledge) {
        @SuppressWarnings("unchecked")
        Map<String, Object> projected = objectMapper.convertValue(
                project(selectedKnowledge), Map.class);
        // Diagnostics remain deterministic Core observability, not AI-facing knowledge.
        projected.remove("relationshipDiagnostics");
        return new LinkedHashMap<>(projected);
    }

    /** Projects only the v3 provider contract; Core-only binding metadata stays internal. */
    public Map<String, Object> toTypedArchitectureOverviewMap(SelectedKnowledge selectedKnowledge) {
        Map<String, Object> legacy = toMap(selectedKnowledge);
        AiReferenceRegistry registry = AiReferenceRegistryFactory.create(selectedKnowledge);
        Map<String, Object> context = new LinkedHashMap<>(legacy);
        context.put("selectedFacts", typedItems(legacy.get("selectedFacts"), registry,
                AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT));
        context.put("selectedObservations", typedItems(legacy.get("selectedObservations"), registry,
                AiReferenceType.OBSERVATION, AiReferenceScope.ANALYSIS_CONTEXT));
        context.put("selectedInsights", typedItems(legacy.get("selectedInsights"), registry,
                AiReferenceType.INSIGHT, AiReferenceScope.PROJECT));
        context.put("existingArchitectureKnowledge",
                typedArchitectureKnowledge(legacy.get("existingArchitectureKnowledge"), registry));
        context.put("repositoryContext", typedRepositoryContext(legacy.get("repositoryContext"), registry));
        context.put("relationshipHighlights", typedRelationships(legacy.get("relationshipHighlights"), registry));
        context.put("semanticSections", typedSemanticSections(legacy.get("semanticSections"), registry));
        Map<String, Object> groundingCandidates = new LinkedHashMap<>();
        groundingCandidates.put("facts", references(registry, "SUPPORTING_FACT"));
        groundingCandidates.put("observations", references(registry, "SUPPORTING_OBSERVATION"));
        groundingCandidates.put("evidence", references(registry, "EVIDENCE_REFERENCE"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("context", context);
        result.put("groundingCandidates", groundingCandidates);
        result.put("selectionMetadata", legacy.get("selectionMetadata"));
        result.put("selectionDigest", legacy.get("selectionDigest"));
        return result;
    }

    private List<Map<String, Object>> typedItems(Object value, AiReferenceRegistry registry,
            AiReferenceType type, AiReferenceScope scope) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(raw -> {
            Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) raw);
            Object id = item.remove("id");
            item.put("reference", requiredReference(registry, type, scope, id,
                    "selected " + type + " item"));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> typedArchitectureKnowledge(Object value,
            AiReferenceRegistry registry) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(raw -> {
            Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) raw);
            Object id = item.remove("insightId");
            item.put("reference", requiredReference(registry, AiReferenceType.INSIGHT,
                    AiReferenceScope.PROJECT, id, "architecture knowledge item"));
            return item;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private Object typedRepositoryContext(Object value, AiReferenceRegistry registry) {
        if (!(value instanceof Map<?, ?> raw)) return value;
        Map<String, Object> context = new LinkedHashMap<>((Map<String, Object>) raw);
        Object evidence = context.get("evidence");
        if (evidence instanceof List<?> list) {
            context.put("evidence", list.stream().filter(Map.class::isInstance).map(item -> {
                Map<String, Object> projected = new LinkedHashMap<>((Map<String, Object>) item);
                Object reference = projected.remove("reference");
                projected.put("aiReference", requiredReference(registry,
                        AiReferenceType.REPOSITORY_EVIDENCE, AiReferenceScope.REPOSITORY,
                        reference, "repository evidence"));
                return projected;
            }).toList());
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> typedRelationships(Object value, AiReferenceRegistry registry) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(raw -> {
            Map<String, Object> relationship = new LinkedHashMap<>((Map<String, Object>) raw);
            relationship.put("source", typedEndpoint(relationship.get("source"), registry));
            relationship.put("target", typedEndpoint(relationship.get("target"), registry));
            return relationship;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> typedSemanticSections(Object value, AiReferenceRegistry registry) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(raw -> {
            Map<String, Object> section = new LinkedHashMap<>((Map<String, Object>) raw);
            Object items = section.get("items");
            if (items instanceof List<?> itemList) {
                section.put("items", itemList.stream().filter(Map.class::isInstance).map(item -> {
                    Map<String, Object> projected = new LinkedHashMap<>((Map<String, Object>) item);
                    String type = String.valueOf(projected.remove("itemType"));
                    Object identity = projected.remove("itemId");
                    projected.put("reference", requiredSemanticReference(registry, type, identity));
                    return projected;
                }).toList());
            }
            return section;
        }).toList();
    }

    private java.util.Optional<AiReference> referenceForItem(AiReferenceRegistry registry,
            String type, String identity) {
        return switch (type) {
            case "FACT" -> registry.referenceFor(AiReferenceType.FACT,
                    AiReferenceScope.ANALYSIS_CONTEXT, identity);
            case "OBSERVATION" -> registry.referenceFor(AiReferenceType.OBSERVATION,
                    AiReferenceScope.ANALYSIS_CONTEXT, identity);
            case "INSIGHT", "ARCHITECTURE_KNOWLEDGE" -> registry.referenceFor(AiReferenceType.INSIGHT,
                    AiReferenceScope.PROJECT, identity);
            case "ANALYSIS" -> registry.referenceFor(AiReferenceType.ANALYSIS,
                    AiReferenceScope.ANALYSIS_CONTEXT, identity);
            case "PROJECT" -> registry.referenceFor(AiReferenceType.PROJECT,
                    AiReferenceScope.PROJECT, identity);
            case "PROJECT_PROFILE" -> registry.referenceFor(AiReferenceType.PROJECT_PROFILE,
                    AiReferenceScope.ANALYSIS_CONTEXT, identity);
            case "HUMAN_CONTEXT" -> registry.referenceFor(AiReferenceType.HUMAN_CONTEXT,
                    AiReferenceScope.PROJECT, identity);
            case "ENGINEERING_EVENT" -> registry.referenceFor(AiReferenceType.ENGINEERING_EVENT,
                    AiReferenceScope.PROJECT, identity);
            case "REPOSITORY_EVIDENCE" -> registry.referenceFor(AiReferenceType.REPOSITORY_EVIDENCE,
                    AiReferenceScope.REPOSITORY, identity);
            default -> throw mappingFailure("Unsupported semantic section item type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private Object typedEndpoint(Object value, AiReferenceRegistry registry) {
        if (!(value instanceof Map<?, ?> raw)) throw mappingFailure("Relationship endpoint is malformed");
        Map<String, Object> endpoint = new LinkedHashMap<>((Map<String, Object>) raw);
        String kind = String.valueOf(endpoint.remove("entityType"));
        Object identity = endpoint.remove("entityId");
        AiReferenceType type = "INSIGHT".equals(kind) ? AiReferenceType.INSIGHT
                : "ENGINEERING_EVENT".equals(kind) ? AiReferenceType.ENGINEERING_EVENT
                : "REPOSITORY_EVIDENCE".equals(kind) ? AiReferenceType.REPOSITORY_EVIDENCE
                : null;
        if (type == null) throw mappingFailure("Unsupported relationship endpoint type: " + kind);
        AiReferenceScope scope = type == AiReferenceType.REPOSITORY_EVIDENCE
                ? AiReferenceScope.REPOSITORY : AiReferenceScope.PROJECT;
        endpoint.put("reference", requiredReference(registry, type, scope, identity,
                "relationship endpoint"));
        return endpoint;
    }

    private AiReference requiredSemanticReference(AiReferenceRegistry registry, String type,
            Object identity) {
        if (identity == null) throw mappingFailure("Semantic section item identity is missing");
        return referenceForItem(registry, type, identity.toString())
                .orElseThrow(() -> mappingFailure("Semantic section item is not mapped"));
    }

    private AiReference requiredReference(AiReferenceRegistry registry, AiReferenceType type,
            AiReferenceScope scope, Object identity, String description) {
        if (identity == null) throw mappingFailure(description + " identity is missing");
        return registry.referenceFor(type, scope, identity.toString())
                .orElseThrow(() -> mappingFailure(description + " is not mapped"));
    }

    private AiReferenceResolutionException mappingFailure(String message) {
        return new AiReferenceResolutionException("REFERENCE_MAPPING_FAILURE", message);
    }

    private List<AiReference> references(AiReferenceRegistry registry, String capability) {
        return registry.groundingCandidates(capability).stream()
                .map(binding -> binding.reference()).toList();
    }

    PromptProjection project(SelectedKnowledge selectedKnowledge) {
        RelationshipProjection relationships = relationshipProjection(selectedKnowledge);
        return new PromptProjection(
                selectedKnowledge.project(),
                selectedKnowledge.analysis(),
                projectProfile(selectedKnowledge.projectProfile()),
                selectedKnowledge.selectedFacts(),
                selectedKnowledge.selectedObservations(),
                selectedKnowledge.diagnostics(),
                selectedKnowledge.selectedInsights().stream().map(this::projectInsight).toList(),
                selectedKnowledge.existingArchitectureKnowledge(),
                selectedKnowledge.selectedEngineeringEvents(),
                selectedKnowledge.selectedHumanContextInputs(),
                relationships.highlights(),
                relationships.diagnostics(),
                semanticSectionComposer.compose(selectedKnowledge),
                projectRepositoryContext(selectedKnowledge.repositoryContext()),
                selectedKnowledge.evolutionContext(),
                selectedKnowledge.selectionMetadata(),
                selectedKnowledge.selectionDigest()
        );
    }

    private RelationshipProjection relationshipProjection(SelectedKnowledge selectedKnowledge) {
        Set<UUID> selectedInsightIds = new HashSet<>(selectedKnowledge.selectedInsights().stream()
                .map(SelectedKnowledge.InsightSnapshot::id)
                .toList());
        Set<UUID> selectedEngineeringEventIds = new HashSet<>(selectedKnowledge
                .selectedEngineeringEvents().stream()
                .map(ProjectContextSnapshot.EngineeringEventSnapshot::id)
                .toList());
        Set<String> selectedRepositoryEvidence = selectedKnowledge.repositoryContext() == null
                ? Set.of()
                : selectedKnowledge.repositoryContext().evidence().stream()
                .map(evidence -> evidence.kind() + "|" + evidence.reference())
                .collect(java.util.stream.Collectors.toSet());
        List<EngineeringRelationship> relationships = selectedKnowledge.engineeringRelationships().isEmpty()
                ? selectedKnowledge.knowledgeRelations().stream().map(EngineeringRelationship::fromKnowledge).toList()
                : selectedKnowledge.engineeringRelationships();
        Comparator<EngineeringRelationship> ordering = Comparator
                .comparing(EngineeringRelationship::relationType)
                .thenComparing(relation -> relation.source() == null ? "" : relation.source().kind())
                .thenComparing(relation -> relation.source() == null ? "" : relation.source().canonicalIdentity())
                .thenComparing(relation -> relation.target() == null ? "" : relation.target().kind())
                .thenComparing(relation -> relation.target() == null ? "" : relation.target().canonicalIdentity())
                .thenComparing(EngineeringRelationship::id);
        List<PromptRelationshipHighlight> highlights = new java.util.ArrayList<>();
        List<PromptRelationshipDiagnostic> diagnostics = new java.util.ArrayList<>();
        for (EngineeringRelationship relation : relationships.stream().sorted(ordering).toList()) {
            boolean sourceProjected = isSelectedProjectedEndpoint(relation.source(), selectedInsightIds,
                    selectedEngineeringEventIds, selectedRepositoryEvidence);
            boolean targetProjected = isSelectedProjectedEndpoint(relation.target(), selectedInsightIds,
                    selectedEngineeringEventIds, selectedRepositoryEvidence);
            if (!sourceProjected || !targetProjected) {
                diagnostics.add(diagnostic(relation,
                        !sourceProjected && !targetProjected
                                ? "SOURCE_AND_TARGET_ENDPOINT_NOT_PROJECTED"
                                : !sourceProjected
                                ? "SOURCE_ENDPOINT_NOT_PROJECTED"
                                : "TARGET_ENDPOINT_NOT_PROJECTED"));
                continue;
            }
            if (highlights.size() >= MAX_RELATIONSHIP_HIGHLIGHTS) {
                diagnostics.add(diagnostic(relation, "RELATIONSHIP_BUDGET_EXCEEDED"));
                continue;
            }
            highlights.add(new PromptRelationshipHighlight(
                    relation.relationType(), endpoint(relation.source()), endpoint(relation.target())
            ));
        }
        return new RelationshipProjection(List.copyOf(highlights), List.copyOf(diagnostics));
    }

    private PromptRelationshipDiagnostic diagnostic(
            EngineeringRelationship relation,
            String reason) {
        return new PromptRelationshipDiagnostic(relation.id(), relation.relationType(),
                endpoint(relation.source()), endpoint(relation.target()), reason);
    }

    private boolean isSelectedProjectedEndpoint(
            EngineeringRelationship.Endpoint endpoint,
            Set<UUID> selectedInsightIds,
            Set<UUID> selectedEngineeringEventIds,
            Set<String> selectedRepositoryEvidence
    ) {
        if (endpoint == null) return false;
        if (endpoint instanceof EngineeringRelationship.RepositoryCommitEndpoint
                || endpoint instanceof EngineeringRelationship.RepositoryFileEndpoint) {
            return endpoint.canonicalIdentity() != null;
        }
        var knowledge = (EngineeringRelationship.KnowledgeEndpoint) endpoint;
        return switch (knowledge.entityType()) {
            case INSIGHT -> selectedInsightIds.contains(knowledge.entityId())
                    || selectedRepositoryEvidence.contains("INSIGHT|insight:" + knowledge.entityId());
            case ENGINEERING_EVENT -> selectedEngineeringEventIds.contains(knowledge.entityId())
                    || selectedRepositoryEvidence.contains("ENGINEERING_EVENT|event:" + knowledge.entityId());
            case DECISION -> selectedRepositoryEvidence.contains(
                    "DECISION|decision:" + knowledge.entityId());
            case CHALLENGE -> selectedRepositoryEvidence.contains(
                    "CHALLENGE|challenge:" + knowledge.entityId());
        };
    }

    private PromptRelationshipEndpoint endpoint(EngineeringRelationship.Endpoint endpoint) {
        if (endpoint == null) return null;
        String identity = endpoint instanceof EngineeringRelationship.KnowledgeEndpoint knowledge
                ? knowledge.entityId().toString()
                : endpoint.canonicalIdentity();
        return new PromptRelationshipEndpoint(endpoint.kind(), identity);
    }

    private PromptInsightSnapshot projectInsight(SelectedKnowledge.InsightSnapshot insight) {
        return new PromptInsightSnapshot(
                insight.id(),
                insight.type(),
                insight.severity(),
                insight.title(),
                insight.content()
        );
    }

    private PromptRepositoryContext projectRepositoryContext(RepositoryContext repositoryContext) {
        if (repositoryContext == null) {
            return null;
        }
        return new PromptRepositoryContext(
                repositoryContext.contextVersion(),
                repositoryContext.profile(),
                repositoryContext.evidence().stream().map(this::projectEvidence).toList(),
                repositoryContext.warnings(),
                repositoryContext.contextDigest()
        );
    }

    private PromptRepositoryEvidence projectEvidence(RepositoryEvidence evidence) {
        return new PromptRepositoryEvidence(
                evidence.layer(),
                evidence.kind(),
                evidence.reference(),
                evidence.summary(),
                evidence.occurredAt(),
                evidence.relatedReferences(),
                projectContent(evidence.content()),
                projectSymbols(evidence.symbols())
        );
    }

    private PromptRepositoryContent projectContent(RepositoryEvidenceContent content) {
        if (content == null) return null;
        return new PromptRepositoryContent(
                content.status(),
                content.text(),
                content.reason(),
                content.policyId(),
                content.policyVersion(),
                content.revision(),
                content.allocationPolicyId(),
                content.allocationPolicyVersion(),
                content.allocationRank()
        );
    }

    private PromptRepositorySymbols projectSymbols(RepositoryEvidenceSymbols symbols) {
        if (symbols == null) return null;
        return new PromptRepositorySymbols(
                symbols.status(),
                symbols.reason(),
                symbols.policyId(),
                symbols.policyVersion(),
                symbols.extractorId(),
                symbols.extractorVersion(),
                symbols.revision(),
                symbols.allocationRank(),
                symbols.truncated(),
                symbols.returnedSymbolCount(),
                symbols.availableSymbolCount(),
                symbols.declarations()
        );
    }

    private PromptProjectProfile projectProfile(ProjectProfileResponse profile) {
        if (profile == null) return null;
        return new PromptProjectProfile(
                profile.id(),
                profile.projectId(),
                profile.analysisId(),
                profile.profileVersion(),
                profile.rendererVersion(),
                profile.generatedAt(),
                profile.requestedRevision(),
                profile.resolvedRevisions(),
                profile.completeness(),
                profile.sections().stream().map(this::projectProfileSection).toList(),
                profile.deterministicSummary(),
                profile.characteristicCount()
        );
    }

    private Map<String, Object> projectProfileSection(Map<String, Object> section) {
        return projectProfileValue(section);
    }

    @SuppressWarnings("unchecked")
    private <T> T projectProfileValue(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> projected = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if ("sourceObservationIds".equals(key) || "sourceObservations".equals(key)) return;
                projected.put(String.valueOf(key), projectProfileValue(nested));
            });
            return (T) Collections.unmodifiableMap(projected);
        }
        if (value instanceof List<?> list) {
            return (T) list.stream().map(this::projectProfileValue).toList();
        }
        return value;
    }

    record PromptProjection(
            AnalysisContext.ProjectSnapshot project,
            AnalysisContext.AnalysisSnapshot analysis,
            PromptProjectProfile projectProfile,
            List<AnalysisContext.FactSnapshot> selectedFacts,
            List<AnalysisContext.ObservationSnapshot> selectedObservations,
            SelectedKnowledge.DiagnosticSnapshot diagnostics,
            List<PromptInsightSnapshot> selectedInsights,
            List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringEventSnapshot>
                    selectedEngineeringEvents,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.HumanContextInputSnapshot>
                    selectedHumanContextInputs,
            List<PromptRelationshipHighlight> relationshipHighlights,
            List<PromptRelationshipDiagnostic> relationshipDiagnostics,
            List<SemanticSection.PromptSemanticSection> semanticSections,
            PromptRepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectedKnowledge.SelectionMetadata selectionMetadata,
            String selectionDigest
    ) { }

    record PromptProjectProfile(
            java.util.UUID id,
            java.util.UUID projectId,
            java.util.UUID analysisId,
            String profileVersion,
            String rendererVersion,
            Instant generatedAt,
            String requestedRevision,
            Map<String, Object> resolvedRevisions,
            ProjectProfileResponse.Completeness completeness,
            List<Map<String, Object>> sections,
            String deterministicSummary,
            int characteristicCount
    ) { }

    record PromptInsightSnapshot(
            java.util.UUID id,
            Object type,
            Object severity,
            String title,
            String content
    ) { }

    record PromptRelationshipHighlight(
            String relationType,
            PromptRelationshipEndpoint source,
            PromptRelationshipEndpoint target
    ) { }

    record PromptRelationshipDiagnostic(
            String relationId,
            String relationType,
            PromptRelationshipEndpoint source,
            PromptRelationshipEndpoint target,
            String reason
    ) { }

    record PromptRelationshipEndpoint(
            String entityType,
            String entityId
    ) { }

    private record RelationshipProjection(
            List<PromptRelationshipHighlight> highlights,
            List<PromptRelationshipDiagnostic> diagnostics
    ) { }

    record PromptRepositoryContext(
            String contextVersion,
            Object profile,
            List<PromptRepositoryEvidence> evidence,
            List<String> warnings,
            String contextDigest
    ) { }

    record PromptRepositoryEvidence(
            Object layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            List<String> relatedReferences,
            PromptRepositoryContent content,
            PromptRepositorySymbols symbols
    ) { }

    record PromptRepositoryContent(
            Object status,
            String text,
            String reason,
            String policyId,
            String policyVersion,
            String revision,
            String allocationPolicyId,
            String allocationPolicyVersion,
            Integer allocationRank
    ) { }

    record PromptRepositorySymbols(
            Object status,
            String reason,
            String policyId,
            String policyVersion,
            String extractorId,
            String extractorVersion,
            String revision,
            Integer allocationRank,
            boolean truncated,
            int returnedSymbolCount,
            Integer availableSymbolCount,
            List<RepositoryEvidenceSymbols.JavaDeclaration> declarations
    ) { }
}
