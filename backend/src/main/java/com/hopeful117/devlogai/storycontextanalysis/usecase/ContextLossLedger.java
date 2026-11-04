package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded Core-side accounting of evidence loss across the Story Context path. */
public record ContextLossLedger(
        String version,
        int maximumDetailEntries,
        Counts counts,
        Map<String, Integer> lossReasons,
        List<Entry> entries,
        int detailOverflowCount,
        Map<String, Object> budgets,
        Map<String, Object> projection,
        Map<String, Object> providerOutcome,
        String digest
) {
    public static final String VERSION = "story-context-loss-ledger-v1";
    public static final int MAXIMUM_DETAIL_ENTRIES = 256;

    public ContextLossLedger {
        if (!VERSION.equals(version)) throw new IllegalArgumentException("Unsupported ledger version");
        if (maximumDetailEntries < 1 || maximumDetailEntries > MAXIMUM_DETAIL_ENTRIES) {
            throw new IllegalArgumentException("Ledger detail bound is invalid");
        }
        counts = counts == null ? Counts.empty() : counts;
        lossReasons = Map.copyOf(lossReasons == null ? Map.of() : lossReasons);
        entries = List.copyOf(entries == null ? List.of() : entries);
        budgets = Map.copyOf(budgets == null ? Map.of() : budgets);
        projection = Map.copyOf(projection == null ? Map.of() : projection);
        providerOutcome = Map.copyOf(providerOutcome == null ? Map.of() : providerOutcome);
        if (entries.size() > maximumDetailEntries) {
            throw new IllegalArgumentException("Ledger detail bound exceeded");
        }
        if (detailOverflowCount < 0 || digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Ledger identity is invalid");
        }
    }

    public static ContextLossLedger capture(
            CanonicalEngineeringContext canonical,
            Map<String, Object> projection,
            StoryContextBudgetPreflight.Result preflight,
            ObjectMapper objectMapper
    ) {
        if (canonical == null || objectMapper == null) {
            throw new IllegalArgumentException("canonical context and object mapper are required");
        }
        RepositoryContext context = canonical.repositoryContext();
        List<RepositoryEvidence> evidence = context == null ? List.of() : context.evidence();
        Map<String, RepositoryContext.SelectionDecision> decisions = context == null
                ? Map.of()
                : context.selectionDecisions().stream().collect(java.util.stream.Collectors.toMap(
                RepositoryContext.SelectionDecision::evidenceReference,
                value -> value,
                (left, right) -> left,
                LinkedHashMap::new));
        Set<String> projected = projectedReferences(projection);
        Map<String, Map<?, ?>> projectedEvidence = projectedEvidence(projection);
        Set<String> grounded = canonical.authorizedReferences().stream()
                .map(EvidenceRef::reference).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean providerAuthorized = preflight != null && preflight.accepted();

        List<Entry> allEntries = new ArrayList<>();
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (RepositoryEvidence item : evidence) {
            RepositoryContext.SelectionDecision decision = decisions.get(item.reference());
            String enrichmentStatus = item.content() == null ? null : item.content().status().name();
            String enrichmentReason = item.content() == null ? null : item.content().reason();
            String lossReason = enrichmentLossReason(enrichmentStatus, enrichmentReason);
            boolean groundedEntry = grounded.contains(item.reference()) && revisionMatchesReference(item);
            if (lossReason == null && decision != null && decision.selected() && !projected.contains(item.reference())) {
                lossReason = "PROJECTION_EVIDENCE_REMOVED";
            } else if (lossReason == null && decision != null && decision.selected()) {
                lossReason = projectionCompactionReason(item, projectedEvidence.get(item.reference()));
            } else if (lossReason == null && projected.contains(item.reference()) && !groundedEntry) {
                lossReason = "GROUNDING_NOT_AUTHORIZED";
            }
            if (lossReason != null) reasons.merge(lossReason, 1, Integer::sum);
            allEntries.add(new Entry(
                    item.reference(), item.kind(), item.provenance() == null ? null
                            : item.provenance().originatingFile(), revision(item),
                    canonical.trustByReference().getOrDefault(item.reference(), "UNKNOWN"),
                    item.provenance() != null && item.reference() != null,
                    decision != null && decision.selected(),
                    decision == null ? "NOT_RECORDED" : decision.reason(),
                    enrichmentStatus, enrichmentReason, projected.contains(item.reference()),
                     groundedEntry, false,
                    lossReason, decision == null ? null : decision.estimatedTokens(),
                    item.estimatedTokens()));
        }
        Set<String> recorded = allEntries.stream().map(Entry::reference)
                .collect(java.util.stream.Collectors.toSet());
        if (context != null) {
            for (RepositoryContext.SelectionDecision decision : context.selectionDecisions()) {
                if (decision.selected() || !recorded.add(decision.evidenceReference())) continue;
                String lossReason = selectionLossReason(decision.reason());
                reasons.merge(lossReason, 1, Integer::sum);
                allEntries.add(new Entry(decision.evidenceReference(), null, null, null,
                        "UNKNOWN", false, false, decision.reason(), null, null, false,
                        false, false, lossReason, decision.estimatedTokens(), 0));
            }
        }
        if (context != null && context.truncated()) reasons.merge("CORE_SELECTION_REDUCED", 1, Integer::sum);
        if (preflight != null && !preflight.accepted()) {
            reasons.merge("PROMPT_PREFLIGHT_REJECTED", 1, Integer::sum);
            if (preflight.code() != null) reasons.merge(preflight.code(), 1, Integer::sum);
        }
        int overflow = Math.max(0, allEntries.size() - MAXIMUM_DETAIL_ENTRIES);
        List<Entry> bounded = allEntries.stream()
                .sorted(Comparator.comparing(Entry::reference, Comparator.nullsLast(String::compareTo)))
                .limit(MAXIMUM_DETAIL_ENTRIES)
                .toList();
        int selected = (int) evidence.stream().filter(item -> decisions.get(item.reference()) != null
                && decisions.get(item.reference()).selected()).count();
        int providerVisible = 0;
        Counts counts = new Counts(
                 context == null ? 0 : context.candidateCount(),
                 context == null ? 0 : uniqueCandidateCount(context, evidence),
                 context == null ? 0 : context.diagnostics().duplicateCandidateCount(),
                 context == null ? 0 : context.selectionDecisions().size(),
                selected,
                 (int) evidence.stream().filter(ContextLossLedger::wasEnriched).count(),
                 projected.size(), (int) allEntries.stream().filter(Entry::grounded).count(), providerVisible,
                context == null ? 0 : context.discardedCount());
        Map<String, Object> budget = new LinkedHashMap<>();
        if (context != null) {
            budget.put("selectionUsedTokens", context.usedTokens());
            budget.put("selectionMaximumTokens", context.budget().maximumTokens());
            budget.put("measurementUnit", "tokens");
            budget.put("measurementStatus", "EXACT");
        }
        if (preflight != null) {
            budget.put("promptInputEstimate", preflight.inputEstimate());
            budget.put("providerOutputLimit", preflight.outputLimit());
            budget.put("modelCapacity", preflight.capacity());
            budget.put("preflightOutcome", preflight.outcome());
            budget.put("providerVisibilityStatus", providerAuthorized
                    ? "AUTHORIZED_NOT_MEASURED" : "NOT_SUBMITTED");
        }
        Map<String, Object> projectionAccounting = projectionAccounting(projection);
        Map<String, Object> providerOutcome = Map.of(
                "status", providerAuthorized ? "AUTHORIZED_NOT_YET_MEASURED" : "NOT_SUBMITTED",
                "visibility", "UNKNOWN",
                "outcomeSource", "AI_INTERACTION_TRACE");
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("version", VERSION);
        identity.put("counts", counts);
        identity.put("lossReasons", reasons);
        identity.put("entries", bounded);
        identity.put("overflow", overflow);
        identity.put("budgets", budget);
        identity.put("projection", projectionAccounting);
        identity.put("providerOutcome", providerOutcome);
        return new ContextLossLedger(VERSION, MAXIMUM_DETAIL_ENTRIES, counts, reasons,
                bounded, overflow, budget, projectionAccounting, providerOutcome,
                digest(objectMapper.writeValueAsBytes(identity)));
    }

    private static Set<String> projectedReferences(Map<String, Object> projection) {
        if (projection == null) return Set.of();
        Object context = projection.get("context");
        if (!(context instanceof Map<?, ?> contextMap)) return Set.of();
        Object raw = contextMap.get("repositoryEvidence");
        if (!(raw instanceof List<?> values)) return Set.of();
        Set<String> references = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map && map.get("reference") instanceof String reference
                    && !reference.isBlank()) references.add(reference);
        }
        return references;
    }

    private static Map<String, Map<?, ?>> projectedEvidence(Map<String, Object> projection) {
        if (projection == null) return Map.of();
        Object context = projection.get("context");
        if (!(context instanceof Map<?, ?> contextMap)
                || !(contextMap.get("repositoryEvidence") instanceof List<?> values)) return Map.of();
        Map<String, Map<?, ?>> result = new LinkedHashMap<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map && map.get("reference") instanceof String reference) {
                result.put(reference, map);
            }
        }
        return result;
    }

    private static String projectionCompactionReason(RepositoryEvidence item, Map<?, ?> projected) {
        if (projected == null) return "PROJECTION_EVIDENCE_REMOVED";
        if (item.content() != null && item.content().text() != null
                && !hasProjectedText(projected.get("content"))) {
            return "PROJECTION_CONTENT_COMPACTED";
        }
        if (item.symbols() != null && !item.symbols().declarations().isEmpty()
                && !hasProjectedDeclarations(projected.get("symbols"))) {
            return "PROJECTION_DECLARATIONS_COMPACTED";
        }
        if (!item.relatedReferences().isEmpty()) {
            if (!(projected.get("relatedReferences") instanceof List<?> related)
                    || related.size() < item.relatedReferences().size()) {
                return "PROJECTION_RELATED_REFERENCES_REMOVED";
            }
        }
        if (item.summary() != null && projected.get("summary") instanceof String summary
                && summary.length() < item.summary().length()) {
            return "PROJECTION_SUMMARY_COMPACTED";
        }
        if (projected.get("reasons") instanceof List<?> reasons
                && reasons.size() < item.rankingReasons().size()) {
            return "PROJECTION_REASONS_COMPACTED";
        }
        return null;
    }

    private static boolean hasProjectedText(Object value) {
        return value instanceof Map<?, ?> map && map.get("text") instanceof String text && !text.isBlank();
    }

    private static boolean hasProjectedDeclarations(Object value) {
        return value instanceof Map<?, ?> map && map.get("declarations") instanceof List<?> declarations
                && !declarations.isEmpty();
    }

    private static Map<String, Object> projectionAccounting(Map<String, Object> projection) {
        if (projection == null) return Map.of("status", "UNKNOWN");
        Object accounting = projection.get("accounting");
        Map<String, Object> result = new LinkedHashMap<>();
        if (accounting instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    result.put(key, entry.getValue());
                }
            }
        }
        Object warnings = projection.get("context") instanceof Map<?, ?> context
                ? context.get("warnings") : null;
        result.put("warningCount", warnings instanceof List<?> list ? list.size() : 0);
        if (projection.get("projectionDigest") != null) {
            result.put("projectionDigest", projection.get("projectionDigest"));
        }
        result.put("status", accounting == null ? "UNKNOWN" : "MEASURED");
        return result;
    }

    private static String enrichmentLossReason(String status, String reason) {
        if (status == null) return null;
        return switch (status) {
            case "SKIPPED" -> reason == null ? "ENRICHMENT_SKIPPED" : reason;
            case "TRUNCATED" -> "ENRICHMENT_TRUNCATED";
            case "UNAVAILABLE" -> "ENRICHMENT_UNAVAILABLE";
            default -> null;
        };
    }

    private static int uniqueCandidateCount(RepositoryContext context, List<RepositoryEvidence> evidence) {
        if (context.diagnostics().uniqueCandidateCount() > 0) {
            return context.diagnostics().uniqueCandidateCount();
        }
        Set<String> references = new LinkedHashSet<>(context.selectionDecisions().stream()
                .map(RepositoryContext.SelectionDecision::evidenceReference)
                .filter(value -> value != null && !value.isBlank()).toList());
        references.addAll(evidence.stream().map(RepositoryEvidence::reference)
                .filter(value -> value != null && !value.isBlank()).toList());
        return references.size();
    }

    private static boolean wasEnriched(RepositoryEvidence item) {
        boolean content = item.content() != null
                && !Set.of("SKIPPED", "UNAVAILABLE").contains(item.content().status().name());
        boolean symbols = item.symbols() != null
                && !Set.of("SKIPPED", "UNAVAILABLE", "UNSUPPORTED", "FAILED")
                .contains(item.symbols().status().name());
        return content || symbols;
    }

    private static boolean revisionMatchesReference(RepositoryEvidence item) {
        String revision = revision(item);
        if (revision == null || item.reference() == null) return false;
        int separator = item.reference().lastIndexOf('@');
        return separator < 0 || revision.equals(item.reference().substring(separator + 1));
    }

    private static String selectionLossReason(String reason) {
        if (reason == null) return "SELECTION_REJECTED";
        return switch (reason) {
            case "EVIDENCE_ITEM_BUDGET_EXCEEDED" -> "SELECTION_ITEM_BUDGET_EXCEEDED";
            case "TOKEN_BUDGET_EXCEEDED" -> "SELECTION_TOKEN_BUDGET_EXCEEDED";
            default -> "SELECTION_" + reason;
        };
    }

    private static String revision(RepositoryEvidence item) {
        if (item.content() != null && item.content().revision() != null) return item.content().revision();
        if (item.symbols() != null && item.symbols().revision() != null) return item.symbols().revision();
        return item.extractionMetadata().get("resolvedRevision");
    }

    private static String digest(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public ContextLossLedger withProviderOutcome(
            String status,
            int providerVisibleCount,
            Map<String, Object> outcomeDetails,
            ObjectMapper objectMapper
    ) {
        if (status == null || status.isBlank() || providerVisibleCount < 0 || objectMapper == null) {
            throw new IllegalArgumentException("provider outcome is invalid");
        }
        Counts updatedCounts = new Counts(counts.candidateCount(), counts.uniqueCandidateCount(),
                counts.duplicateCandidateCount(), counts.rankedCount(), counts.selectedCount(),
                counts.enrichedCount(), counts.projectedCount(), counts.groundedCount(),
                providerVisibleCount, counts.discardedCount());
        Map<String, Object> updatedOutcome = new LinkedHashMap<>();
        updatedOutcome.put("status", status);
        updatedOutcome.put("visibility", providerVisibleCount > 0 ? "MEASURED" : "UNKNOWN");
        updatedOutcome.put("outcomeSource", "AI_INTERACTION_TRACE");
        if (outcomeDetails != null) updatedOutcome.putAll(outcomeDetails);
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("version", version);
        identity.put("counts", updatedCounts);
        identity.put("lossReasons", lossReasons);
        identity.put("entries", entries);
        identity.put("overflow", detailOverflowCount);
        identity.put("budgets", budgets);
        identity.put("projection", projection);
        identity.put("providerOutcome", updatedOutcome);
        return new ContextLossLedger(version, maximumDetailEntries, updatedCounts, lossReasons,
                entries, detailOverflowCount, budgets, projection, updatedOutcome,
                digest(objectMapper.writeValueAsBytes(identity)));
    }

    public record Counts(
            int candidateCount,
            int uniqueCandidateCount,
            int duplicateCandidateCount,
            int rankedCount,
            int selectedCount,
            int enrichedCount,
            int projectedCount,
            int groundedCount,
            int providerVisibleCount,
            int discardedCount
    ) {
        static Counts empty() { return new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }

    public record Entry(
            String reference,
            String kind,
            String originatingFile,
            String revision,
            String trustTier,
            boolean citable,
            boolean selected,
            String selectionReason,
            String enrichmentStatus,
            String enrichmentReason,
            boolean projected,
            boolean grounded,
            boolean providerVisible,
            String lossReason,
            Integer selectedTokens,
            int finalTokens
    ) { }
}
