package com.hopeful117.devlogai.projectcontext.projection;

import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Service
public class AgentContextProjectionService {
    private static final String RELATED_REFERENCES_REMOVED =
            "AGENT_PROJECTION_RELATED_REFERENCES_REMOVED";
    private static final String REASONS_COMPACTED =
            "AGENT_PROJECTION_REASONS_COMPACTED";
    private static final String DECLARATIONS_REMOVED =
            "AGENT_PROJECTION_DECLARATIONS_REMOVED";
    private static final String CONTENT_REMOVED =
            "AGENT_PROJECTION_CONTENT_REMOVED";
    private static final String EVIDENCE_REMOVED =
            "AGENT_PROJECTION_EVIDENCE_REMOVED";

    private final ObjectMapper objectMapper;
    private final AgentContextProjectionPolicy policy;

    public AgentContextProjectionService(
            ObjectMapper objectMapper,
            AgentContextProjectionPolicy policy
    ) {
        this.objectMapper = objectMapper;
        this.policy = policy;
    }

    public AgentEngineeringStoryContext project(
            UUID projectId,
            ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext,
            Instant generatedAt
    ) {
        ProjectionState state = initial(repositoryContext);
        state = fit(projectId, projectContext, repositoryContext, state);
        CanonicalProjection canonical = canonical(
                projectId, projectContext, repositoryContext, state);
        byte[] bytes = objectMapper.writeValueAsBytes(canonical);
        int estimatedTokens = estimateTokens(bytes.length);
        AgentRepositoryContext.Accounting accounting = new AgentRepositoryContext.Accounting(
                policy.maximumBytes(), policy.maximumEstimatedTokens(),
                bytes.length, estimatedTokens, state.removedRelatedReferences,
                state.removedReasons, state.removedDeclarationPayloads,
                state.removedContentPayloads, state.removedEvidenceItems);
        AgentRepositoryContext projected = repositoryContext(
                repositoryContext, state, digest(bytes), accounting);
        return new AgentEngineeringStoryContext(
                projectContext, generatedAt, projectId, projected);
    }

    private ProjectionState initial(RepositoryContext context) {
        Map<String, String> selectedReasons = new LinkedHashMap<>();
        context.selectionDecisions().stream().filter(RepositoryContext.SelectionDecision::selected)
                .forEach(value -> selectedReasons.putIfAbsent(
                        value.evidenceReference(), value.reason()));
        List<AgentRepositoryContext.Evidence> evidence = context.evidence().stream()
                .map(value -> evidence(value, selectedReasons.get(value.reference())))
                .toList();
        return new ProjectionState(evidence, new ArrayList<>(context.warnings()),
                0, 0, 0, 0, 0);
    }

    private ProjectionState fit(
            UUID projectId,
            ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext,
            ProjectionState initial
    ) {
        ProjectionState state = initial;
        if (fits(projectId, projectContext, repositoryContext, state)) return state;

        state = removeRelatedReferences(state);
        if (fits(projectId, projectContext, repositoryContext, state)) return state;

        state = compactReasons(state);
        if (fits(projectId, projectContext, repositoryContext, state)) return state;

        state = removeDeclarations(state);
        if (fits(projectId, projectContext, repositoryContext, state)) return state;

        state = removeContent(state);
        if (fits(projectId, projectContext, repositoryContext, state)) return state;

        return removeTailEvidence(projectId, projectContext, repositoryContext, state);
    }

    private ProjectionState removeRelatedReferences(ProjectionState state) {
        int related = state.evidence.stream()
                .mapToInt(value -> value.relatedReferences().size()).sum();
        return related == 0 ? state : transform(state,
                value -> value.withRelatedReferences(List.of()),
                RELATED_REFERENCES_REMOVED, related, 0, 0, 0);
    }

    private ProjectionState compactReasons(ProjectionState state) {
        int reasons = state.evidence.stream()
                .mapToInt(value -> Math.max(0, value.reasons().size() - 1)).sum();
        if (reasons == 0) return state;
        return transform(state, value -> value.withReasons(firstReason(value)),
                REASONS_COMPACTED, 0, reasons, 0, 0);
    }

    private List<String> firstReason(AgentRepositoryContext.Evidence evidence) {
        return evidence.reasons().isEmpty()
                ? List.of() : List.of(evidence.reasons().getFirst());
    }

    private ProjectionState removeDeclarations(ProjectionState state) {
        int declarationPayloads = (int) state.evidence.stream()
                .filter(value -> value.symbols() != null
                        && !value.symbols().declarations().isEmpty()).count();
        return declarationPayloads == 0 ? state : transform(state,
                AgentRepositoryContext.Evidence::withoutDeclarations,
                DECLARATIONS_REMOVED, 0, 0, declarationPayloads, 0);
    }

    private ProjectionState removeContent(ProjectionState state) {
        int contentPayloads = (int) state.evidence.stream()
                .filter(value -> value.content() != null
                        && value.content().text() != null).count();
        return contentPayloads == 0 ? state : transform(state,
                AgentRepositoryContext.Evidence::withoutContentText,
                CONTENT_REMOVED, 0, 0, 0, contentPayloads);
    }

    private ProjectionState removeTailEvidence(
            UUID projectId, ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext, ProjectionState state) {
        List<AgentRepositoryContext.Evidence> remaining =
                new ArrayList<>(state.evidence);
        List<String> warnings = new ArrayList<>(state.warnings);
        if (!warnings.contains(EVIDENCE_REMOVED)) warnings.add(EVIDENCE_REMOVED);
        int removed = state.removedEvidenceItems;
        while (remaining.size() > 1) {
            remaining.removeLast();
            removed++;
            ProjectionState candidate = new ProjectionState(List.copyOf(remaining),
                    List.copyOf(warnings), state.removedRelatedReferences,
                    state.removedReasons, state.removedDeclarationPayloads,
                    state.removedContentPayloads, removed);
            if (fits(projectId, projectContext, repositoryContext, candidate)) {
                return candidate;
            }
        }
        throw new AgentContextProjectionException(
                "Agent context cannot fit configured projection limits");
    }

    private ProjectionState transform(
            ProjectionState state,
            UnaryOperator<AgentRepositoryContext.Evidence> transformation,
            String warning,
            int related,
            int reasons,
            int declarations,
            int content
    ) {
        List<String> warnings = new ArrayList<>(state.warnings);
        if (!warnings.contains(warning)) warnings.add(warning);
        return new ProjectionState(state.evidence.stream().map(transformation).toList(),
                List.copyOf(warnings), state.removedRelatedReferences + related,
                state.removedReasons + reasons,
                state.removedDeclarationPayloads + declarations,
                state.removedContentPayloads + content, state.removedEvidenceItems);
    }

    private boolean fits(
            UUID projectId,
            ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext,
            ProjectionState state
    ) {
        int bytes = objectMapper.writeValueAsBytes(canonical(
                projectId, projectContext, repositoryContext, state)).length;
        return bytes <= policy.maximumBytes()
                && estimateTokens(bytes) <= policy.maximumEstimatedTokens();
    }

    private CanonicalProjection canonical(
            UUID projectId,
            ProjectContextSnapshot projectContext,
            RepositoryContext context,
            ProjectionState state
    ) {
        return new CanonicalProjection(projectContext, projectId,
                new CanonicalRepositoryContext(
                        AgentRepositoryContext.VERSION,
                        AgentContextProjectionPolicy.POLICY_ID,
                        AgentContextProjectionPolicy.POLICY_VERSION,
                        context.contextVersion(), context.activeProfileKeys(),
                        context.contextPlanVersion(), resolvedRevisions(state.evidence),
                        state.evidence, selectedByLayer(state.evidence),
                        selectedByKind(state.evidence),
                        rejectedByReason(context), context.candidateCount(),
                        state.evidence.size(), context.discardedCount(),
                        context.diagnostics().duplicateCandidateCount(), context.truncated(),
                        state.warnings, context.contextDigest()));
    }

    private AgentRepositoryContext repositoryContext(
            RepositoryContext context,
            ProjectionState state,
            String projectionDigest,
            AgentRepositoryContext.Accounting accounting
    ) {
        return new AgentRepositoryContext(AgentRepositoryContext.VERSION,
                AgentContextProjectionPolicy.POLICY_ID,
                AgentContextProjectionPolicy.POLICY_VERSION,
                context.contextVersion(), context.activeProfileKeys(),
                context.contextPlanVersion(), resolvedRevisions(state.evidence),
                state.evidence, selectedByLayer(state.evidence),
                selectedByKind(state.evidence),
                rejectedByReason(context), context.candidateCount(),
                state.evidence.size(), context.discardedCount(),
                context.diagnostics().duplicateCandidateCount(), context.truncated(),
                state.warnings, context.contextDigest(), projectionDigest, accounting);
    }

    private AgentRepositoryContext.Evidence evidence(
            RepositoryEvidence value,
            String selectionReason
    ) {
        return new AgentRepositoryContext.Evidence(value.layer().name(), value.kind(),
                value.reference(), value.summary(), value.occurredAt(),
                value.relevanceScore(), reasons(value, selectionReason),
                value.relatedReferences().stream()
                        .limit(policy.maximumRelatedReferencesPerEvidence()).toList(),
                provenance(value), extraction(value), content(value.content()),
                symbols(value.symbols()));
    }

    private List<String> reasons(RepositoryEvidence value, String selectionReason) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (selectionReason != null && !selectionReason.isBlank()) result.add(selectionReason);
        value.rankingReasons().stream().filter(this::actionableReason)
                .forEach(result::add);
        return result.stream().limit(policy.maximumReasonsPerEvidence()).toList();
    }

    private boolean actionableReason(String value) {
        return !(value.startsWith("SEMANTIC_RELEVANCE=")
                || value.startsWith("ARCHITECTURAL_RELEVANCE=")
                || value.startsWith("HISTORICAL_RELEVANCE=")
                || value.startsWith("RECENCY=")
                || value.startsWith("CONFIDENCE=")
                || value.startsWith("USER_GUIDANCE_BOOST=")
                || value.startsWith("FINAL_SCORE=")
                || value.startsWith("RANKING_POLICY:"));
    }

    private AgentRepositoryContext.Provenance provenance(RepositoryEvidence value) {
        if (value.provenance() == null) return null;
        return new AgentRepositoryContext.Provenance(value.provenance().sourceType(),
                value.provenance().repositoryLocation(),
                value.provenance().originatingFile(), value.provenance().identifier());
    }

    private AgentRepositoryContext.Extraction extraction(RepositoryEvidence value) {
        Map<String, String> metadata = value.extractionMetadata();
        String collectorId = metadata.get("collectorId");
        String collectorVersion = metadata.get("collectorVersion");
        String revision = metadata.get("resolvedRevision");
        if (collectorId == null && collectorVersion == null && revision == null) return null;
        return new AgentRepositoryContext.Extraction(
                collectorId, collectorVersion, revision);
    }

    private AgentRepositoryContext.Content content(RepositoryEvidenceContent value) {
        if (value == null) return null;
        return new AgentRepositoryContext.Content(value.status().name(), value.text(),
                value.reason(), value.policyId(), value.policyVersion(), value.revision(),
                value.allocationPolicyId(), value.allocationPolicyVersion(),
                value.allocationRank());
    }

    private AgentRepositoryContext.Symbols symbols(RepositoryEvidenceSymbols value) {
        if (value == null) return null;
        return new AgentRepositoryContext.Symbols(value.status().name(), value.reason(),
                value.policyId(), value.policyVersion(), value.extractorId(),
                value.extractorVersion(), value.revision(), value.allocationRank(),
                value.truncated(), value.returnedSymbolCount(),
                value.availableSymbolCount(), value.declarations());
    }

    private Map<String, Integer> selectedByLayer(
            List<AgentRepositoryContext.Evidence> evidence) {
        Map<String, Integer> result = new TreeMap<>();
        evidence.forEach(value -> result.merge(value.layer(), 1, Integer::sum));
        return immutable(result);
    }

    private Map<String, Integer> selectedByKind(
            List<AgentRepositoryContext.Evidence> evidence) {
        Map<String, Integer> result = new TreeMap<>();
        evidence.forEach(value -> result.merge(value.kind(), 1, Integer::sum));
        return immutable(result);
    }

    private Map<String, Integer> rejectedByReason(RepositoryContext context) {
        Map<String, Integer> result = new TreeMap<>();
        context.selectionDecisions().stream().filter(value -> !value.selected())
                .forEach(value -> result.merge(value.reason(), 1, Integer::sum));
        return immutable(result);
    }

    private Map<String, Integer> immutable(Map<String, Integer> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private List<String> resolvedRevisions(
            List<AgentRepositoryContext.Evidence> evidence
    ) {
        java.util.SortedSet<String> result = new java.util.TreeSet<>();
        for (AgentRepositoryContext.Evidence value : evidence) {
            if (value.extraction() != null) add(result, value.extraction().resolvedRevision());
            if (value.content() != null) add(result, value.content().revision());
            if (value.symbols() != null) add(result, value.symbols().revision());
        }
        return List.copyOf(result);
    }

    private void add(java.util.Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private int estimateTokens(int bytes) {
        return Math.max(1, (bytes + 3) / 4);
    }

    private String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record ProjectionState(
            List<AgentRepositoryContext.Evidence> evidence,
            List<String> warnings,
            int removedRelatedReferences,
            int removedReasons,
            int removedDeclarationPayloads,
            int removedContentPayloads,
            int removedEvidenceItems
    ) { }

    private record CanonicalProjection(
            ProjectContextSnapshot projectContext,
            UUID projectId,
            CanonicalRepositoryContext repositoryContext
    ) { }

    private record CanonicalRepositoryContext(
            String projectionVersion,
            String projectionPolicyId,
            String projectionPolicyVersion,
            String repositoryContextVersion,
            List<String> activeProfileKeys,
            String contextPlanVersion,
            List<String> resolvedRevisions,
            List<AgentRepositoryContext.Evidence> evidence,
            Map<String, Integer> selectedByLayer,
            Map<String, Integer> selectedByKind,
            Map<String, Integer> rejectedByReason,
            int candidateCount,
            int selectedCount,
            int discardedCount,
            int duplicateCandidateCount,
            boolean truncated,
            List<String> warnings,
            String repositoryContextDigest
    ) { }
}
