package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.mapper.TrustTierClassifier;
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
import java.nio.charset.StandardCharsets;

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
        EvaluationTelemetry evaluationTelemetry,
        String digest
) {
    public static final String VERSION = "story-context-loss-ledger-v1";
    public static final String EVALUATION_TELEMETRY_VERSION = "story-context-evaluation-telemetry-v1";
    public static final int MAXIMUM_DETAIL_ENTRIES = 256;
    public static final int MAXIMUM_CANDIDATE_FIXTURE_ROWS = 4096;
    public static final String CANDIDATE_FIXTURE_VERSION = "story-context-candidate-fixture-v1";
    private static final Set<String> TELEMETRY_STAGES = Set.of(
            "CANDIDATE", "SELECTED", "REJECTED", "PROJECTED", "GROUNDED");
    private static final Set<String> TELEMETRY_TRUST = Set.of(
            "TRUSTED", "HUMAN_AUTHORED", "TECHNICAL_EVIDENCE", "SYSTEM_METADATA",
            "UNVALIDATED", "TRANSIENT_AI", "UNKNOWN");
    private static final Set<String> CANDIDATE_KINDS = Set.of(
            "ANALYSIS", "CHALLENGE", "CHANGED_FILE", "COMMIT", "CONFIGURATION_FILES",
            "CONFIG_FILE", "DECISION", "ENGINEERING_STORY", "FACT", "FILE_EXTENSIONS",
            "INSIGHT", "MODULE", "MODULE_SUMMARY", "SOURCE_DIRECTORIES", "SOURCE_FILE",
            "VALIDATABLE_PROPOSAL",
            "TEST_DIRECTORIES", "TEST_FILE");
    private static final Set<String> CANDIDATE_REASONS = Set.of(
            "CATEGORY_CONCENTRATION_LIMIT", "EVIDENCE_ITEM_BUDGET_EXCEEDED",
            "TOKEN_BUDGET_EXCEEDED", "INSUFFICIENT_RELEVANCE", "SELECTED_BY_CATEGORY_FLOOR", "SELECTED_BY_DIVERSITY",
            "SELECTED_BY_RANK", "SELECTED_BY_STRONG_RELEVANCE", "SELECTED_BY_REQUESTED_FILE", "NOT_RECORDED");

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
        evaluationTelemetry = evaluationTelemetry == null ? EvaluationTelemetry.empty() : evaluationTelemetry;
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
        return capture(canonical, projection, preflight, objectMapper, "unbound-test-capture");
    }

    public static ContextLossLedger capture(
            CanonicalEngineeringContext canonical,
            Map<String, Object> projection,
            StoryContextBudgetPreflight.Result preflight,
            ObjectMapper objectMapper,
            String taskId
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
        EvaluationTelemetry telemetry = evaluationTelemetry(canonical, projected, grounded, taskId, projection);
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("version", VERSION);
        identity.put("counts", counts);
        identity.put("lossReasons", reasons);
        identity.put("entries", bounded);
        identity.put("overflow", overflow);
        identity.put("budgets", budget);
        identity.put("projection", projectionAccounting);
        identity.put("providerOutcome", providerOutcome);
        identity.put("evaluationTelemetry", telemetry);
        return new ContextLossLedger(VERSION, MAXIMUM_DETAIL_ENTRIES, counts, reasons,
                bounded, overflow, budget, projectionAccounting, providerOutcome, telemetry,
                digest(objectMapper.writeValueAsBytes(identity)));
    }

    private static EvaluationTelemetry evaluationTelemetry(
            CanonicalEngineeringContext canonical,
            Set<String> projected,
            Set<String> grounded,
            String taskId,
            Map<String, Object> projection
    ) {
        RepositoryContext context = canonical.repositoryContext();
        if (context == null) return EvaluationTelemetry.empty();
        Map<String, RepositoryContext.SelectionDecision> decisions = context.selectionDecisions().stream()
                .collect(java.util.stream.Collectors.toMap(
                        RepositoryContext.SelectionDecision::evidenceReference,
                        value -> value, (left, right) -> left, LinkedHashMap::new));
        Map<String, Integer> stages = new LinkedHashMap<>();
        Map<String, Integer> trust = new LinkedHashMap<>();
        Map<String, RepositoryEvidence> evidenceByReference = context.evidence().stream()
                .filter(item -> item.reference() != null)
                .collect(java.util.stream.Collectors.toMap(RepositoryEvidence::reference,
                        value -> value, (left, right) -> left, LinkedHashMap::new));
        Map<String, RepositoryContext.CandidateMetadata> candidateByReference = context.candidates().stream()
                .filter(item -> item.reference() != null)
                .collect(java.util.stream.Collectors.toMap(RepositoryContext.CandidateMetadata::reference,
                        value -> value, (left, right) -> left, LinkedHashMap::new));
        for (RepositoryContext.SelectionDecision decision : context.selectionDecisions()) {
            RepositoryEvidence item = evidenceByReference.get(decision.evidenceReference());
            RepositoryContext.CandidateMetadata candidate = candidateByReference.get(decision.evidenceReference());
            String kind = item != null && item.kind() != null ? item.kind()
                    : candidate == null || candidate.kind() == null ? "UNKNOWN" : candidate.kind();
            String itemTrust = candidateTrust(item, candidate);
            increment(stages, kind, "CANDIDATE");
            increment(trust, itemTrust, "CANDIDATE");
            if (decision.selected()) {
                increment(stages, kind, "SELECTED");
                increment(trust, itemTrust, "SELECTED");
            } else {
                increment(stages, kind, "REJECTED");
                increment(trust, itemTrust, "REJECTED");
            }
            if (item != null && projected.contains(item.reference())) {
                increment(stages, kind, "PROJECTED");
                increment(trust, itemTrust, "PROJECTED");
            }
            if (item != null && grounded.contains(item.reference()) && revisionMatchesReference(item)) {
                increment(stages, kind, "GROUNDED");
                increment(trust, itemTrust, "GROUNDED");
            }
        }
        for (RepositoryEvidence item : context.evidence()) {
            if (decisions.containsKey(item.reference())) continue;
            String kind = item.kind() == null ? "UNKNOWN" : item.kind();
            increment(stages, kind, "CANDIDATE");
            String itemTrust = candidateTrust(item);
            increment(trust, itemTrust, "CANDIDATE");
            if (projected.contains(item.reference())) {
                increment(stages, kind, "PROJECTED");
                increment(trust, itemTrust, "PROJECTED");
            }
            if (grounded.contains(item.reference()) && revisionMatchesReference(item)) {
                increment(stages, kind, "GROUNDED");
                increment(trust, itemTrust, "GROUNDED");
            }
        }
        List<RequestedFileOutcome> requested = requestedFileOutcomes(
                canonical, decisions, projected, grounded);
        Map<String, Integer> boundedStages = boundedCounts(stages, 128);
        Map<String, Integer> boundedTrust = boundedCounts(trust, 128);
        GroundingSummary groundingSummary = new GroundingSummary(
                canonical.repositoryContext().evidence().size(), projected.size(),
                projected.stream().filter(reference -> grounded.contains(reference)
                        && context.evidence().stream().anyMatch(item ->
                        item.reference().equals(reference) && revisionMatchesReference(item))).count());
        CandidateFixture fixture = candidateFixture(canonical, decisions, projected, grounded, taskId, projection);
        String telemetryDigest = digest(telemetryCanonical(boundedStages, boundedTrust, requested,
                groundingSummary, fixture).getBytes(StandardCharsets.UTF_8));
        return new EvaluationTelemetry(
                EVALUATION_TELEMETRY_VERSION, boundedStages, boundedTrust, requested,
                groundingSummary, fixture, telemetryDigest);
    }

    private static String candidateTrust(RepositoryEvidence item) {
        return item == null ? "UNKNOWN" : TrustTierClassifier.classify(
                item.kind(), item.provenance() == null ? null : item.provenance().sourceType()).name();
    }

    private static String candidateTrust(
            RepositoryEvidence item, RepositoryContext.CandidateMetadata candidate) {
        if (item != null) return candidateTrust(item);
        return candidate == null ? "UNKNOWN" : TrustTierClassifier.classify(
                candidate.kind(), candidate.sourceType()).name();
    }

    private static CandidateFixture candidateFixture(
            CanonicalEngineeringContext canonical,
            Map<String, RepositoryContext.SelectionDecision> decisions,
            Set<String> projected,
            Set<String> grounded,
            String taskId,
            Map<String, Object> projection
    ) {
        List<RepositoryContext.CandidateMetadata> candidates = canonical.repositoryContext().candidates();
        if (candidates.size() > MAXIMUM_CANDIDATE_FIXTURE_ROWS) {
            throw new IllegalArgumentException("Candidate fixture bound exceeded");
        }
        if (candidates.stream().map(RepositoryContext.CandidateMetadata::reference).distinct().count()
                != candidates.size()) {
            throw new IllegalArgumentException("Candidate fixture contains duplicate candidate references");
        }
        Set<String> requested = canonical.requestEcho() == null || canonical.requestEcho().files() == null
                ? Set.of() : canonical.requestEcho().files().stream().filter(value -> value != null && !value.isBlank())
                .map(ContextLossLedger::normalizePath).collect(java.util.stream.Collectors.toSet());
        List<CandidateRow> rows = candidates.stream().map(candidate -> {
            RepositoryContext.SelectionDecision decision = decisions.get(candidate.reference());
            boolean projectedValue = projected.contains(candidate.reference());
            boolean selected = decision != null && decision.selected();
            boolean isGrounded = projectedValue
                    && grounded.contains(candidate.reference())
                    && revisionMatchesReference(canonical.repositoryContext().evidence().stream()
                    .filter(item -> item.reference().equals(candidate.reference())).findFirst().orElse(null));
            String path = requested.stream().anyMatch(value -> candidate.originatingFile() != null
                    && (candidate.originatingFile().equals(value) || candidate.originatingFile().startsWith(value + "/")))
                    ? normalizePath(candidate.originatingFile()) : null;
            return new CandidateRow(candidate.reference(), candidate.kind(), path, candidate.relevanceScore(),
                    TrustTierClassifier.classify(candidate.kind(), candidate.sourceType()).name(),
                    isGrounded, selected, projectedValue,
                    decision == null ? "NOT_RECORDED" : decision.reason());
        }).sorted(Comparator.comparing(CandidateRow::reference)).toList();
        String canonicalRows = rows.stream().map(row -> canonicalFields(
                row.reference(), row.kind(), row.path() == null ? "-" : row.path(),
                Integer.toString(row.score()), row.trust(), Boolean.toString(row.grounded()),
                Boolean.toString(row.selected()), Boolean.toString(row.projected()), row.reason()))
                .collect(java.util.stream.Collectors.joining("|"));
        return new CandidateFixture(CANDIDATE_FIXTURE_VERSION, taskId,
                targetRevision(canonical), canonical.contextDigest(),
                projectionDigest(projection), rows,
                digest(canonicalRows.getBytes(StandardCharsets.UTF_8)));
    }

    private static String projectionDigest(Map<String, Object> projection) {
        Object value = projection == null ? null : projection.get("projectionDigest");
        return value instanceof String digest && digest.matches("[0-9a-f]{64}")
                ? digest : "0".repeat(64);
    }

    private static String targetRevision(CanonicalEngineeringContext canonical) {
        return StoryContextAgentProjectionV1.canonicalRevision(canonical);
    }

    private static String canonicalFields(String... fields) {
        return java.util.Arrays.stream(fields)
                .map(value -> value.getBytes(StandardCharsets.UTF_8).length + ":" + value)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static List<RequestedFileOutcome> requestedFileOutcomes(
            CanonicalEngineeringContext canonical,
            Map<String, RepositoryContext.SelectionDecision> decisions,
            Set<String> projected,
            Set<String> grounded
    ) {
        if (canonical.requestEcho() == null || canonical.requestEcho().files() == null) return List.of();
        List<RequestedFileOutcome> result = new ArrayList<>();
        for (String raw : canonical.requestEcho().files().stream().limit(32).toList()) {
            if (raw == null || raw.isBlank()) continue;
            String requested = normalizePath(raw);
            boolean found = false;
            boolean selected = false;
            boolean projectedValue = false;
            boolean groundedValue = false;
            for (RepositoryEvidence item : canonical.repositoryContext().evidence()) {
                if (!matchesPath(item, requested)) continue;
                found = true;
                RepositoryContext.SelectionDecision decision = decisions.get(item.reference());
                selected |= decision != null && decision.selected();
                projectedValue |= projected.contains(item.reference());
                groundedValue |= grounded.contains(item.reference()) && revisionMatchesReference(item);
            }
            String reason = !found ? "REQUESTED_FILE_NOT_FOUND"
                    : !selected ? "REQUESTED_FILE_NOT_SELECTED"
                    : !projectedValue ? "REQUESTED_FILE_NOT_PROJECTED"
                    : !groundedValue ? "REQUESTED_FILE_NOT_GROUNDED" : null;
            result.add(new RequestedFileOutcome(requested, found, selected, projectedValue,
                    groundedValue, reason));
        }
        return List.copyOf(result);
    }

    private static boolean matchesPath(RepositoryEvidence item, String requested) {
        return item.provenance() != null && item.provenance().originatingFile() != null
                && (normalizePath(item.provenance().originatingFile()).equals(requested)
                || normalizePath(item.provenance().originatingFile()).startsWith(requested + "/"));
    }

    private static String normalizePath(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("//")
                || normalized.contains("../") || normalized.endsWith("/..")
                || normalized.equals("..")) {
            throw new IllegalArgumentException("Requested file path is not canonical");
        }
        return normalized;
    }

    private static boolean isCanonicalPath(String value) {
        if (value == null || value.isBlank() || value.startsWith("/")
                || value.contains("\\") || value.contains("//")
                || value.equals("..") || value.contains("../") || value.endsWith("/..")) {
            return false;
        }
        return true;
    }

    private static void increment(Map<String, Integer> values, String key, String stage) {
        values.merge(key + ":" + stage, 1, Integer::sum);
    }

    private static Map<String, Integer> boundedCounts(Map<String, Integer> values, int maximumKeys) {
        if (values.size() > maximumKeys) {
            throw new IllegalArgumentException("Evaluation telemetry distribution bound exceeded");
        }
        return values.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        Map.Entry::getValue, (left, right) -> left));
    }

    private static String telemetryCanonical(
            Map<String, Integer> stages,
            Map<String, Integer> trust,
            List<RequestedFileOutcome> requested,
            GroundingSummary grounding,
            CandidateFixture fixture
    ) {
        StringBuilder value = new StringBuilder(EVALUATION_TELEMETRY_VERSION);
        appendCounts(value, "stage", stages);
        appendCounts(value, "trust", trust);
        value.append("|requested:");
        requested.forEach(item -> value.append(item.path()).append(',')
                .append(item.found()).append(',').append(item.selected()).append(',')
                .append(item.projected()).append(',').append(item.grounded()).append(',')
                .append(item.absenceReason() == null ? "-" : item.absenceReason()).append(';'));
        value.append("|grounding:").append(grounding.authorizedCount()).append(',')
                .append(grounding.projectedCount()).append(',')
                .append(grounding.projectedGroundedCount());
        value.append("|correlation:").append(fixture.taskId()).append(',')
                .append(fixture.targetRevision()).append(',')
                .append(fixture.contextDigest()).append(',')
                .append(fixture.projectionDigest());
        value.append("|fixture:").append(fixture.digest());
        return value.toString();
    }

    private static void appendCounts(StringBuilder value, String name, Map<String, Integer> counts) {
        value.append('|').append(name).append(':');
        counts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> value.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
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
        if (item == null || revision == null || item.reference() == null) return false;
        int separator = item.reference().lastIndexOf('@');
        return separator >= 0 && revision.equals(item.reference().substring(separator + 1));
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
        identity.put("evaluationTelemetry", evaluationTelemetry);
        return new ContextLossLedger(version, maximumDetailEntries, updatedCounts, lossReasons,
                entries, detailOverflowCount, budgets, projection, updatedOutcome, evaluationTelemetry,
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

    public record EvaluationTelemetry(
            String version,
            Map<String, Integer> stageCounts,
            Map<String, Integer> trustCounts,
            List<RequestedFileOutcome> requestedFileOutcomes,
            GroundingSummary grounding,
            CandidateFixture candidateFixture,
            String digest
    ) {
        public EvaluationTelemetry {
            if (!EVALUATION_TELEMETRY_VERSION.equals(version)
                    || digest == null || !digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Evaluation telemetry identity is invalid");
            }
            stageCounts = Map.copyOf(stageCounts == null ? Map.of() : stageCounts);
            trustCounts = Map.copyOf(trustCounts == null ? Map.of() : trustCounts);
            requestedFileOutcomes = List.copyOf(requestedFileOutcomes == null ? List.of() : requestedFileOutcomes);
            grounding = grounding == null ? new GroundingSummary(0, 0, 0) : grounding;
            candidateFixture = candidateFixture == null ? CandidateFixture.empty() : candidateFixture;
            if (stageCounts.size() > 128 || trustCounts.size() > 128 || requestedFileOutcomes.size() > 32
                    || stageCounts.entrySet().stream().anyMatch(entry -> entry.getValue() < 0)
                    || trustCounts.entrySet().stream().anyMatch(entry -> entry.getValue() < 0)
                    || stageCounts.keySet().stream().anyMatch(EvaluationTelemetry::invalidStageKey)
                    || trustCounts.keySet().stream().anyMatch(EvaluationTelemetry::invalidTrustKey)) {
                throw new IllegalArgumentException("Evaluation telemetry bounds exceeded");
            }
        }

        private static boolean invalidStageKey(String key) {
            int separator = key == null ? -1 : key.lastIndexOf(':');
            return separator <= 0 || separator > 100
                    || !TELEMETRY_STAGES.contains(key.substring(separator + 1));
        }

        private static boolean invalidTrustKey(String key) {
            int separator = key == null ? -1 : key.lastIndexOf(':');
            return separator <= 0 || separator > 100
                    || !TELEMETRY_STAGES.contains(key.substring(separator + 1))
                    || !TELEMETRY_TRUST.contains(key.substring(0, separator));
        }

        static EvaluationTelemetry empty() {
            return new EvaluationTelemetry(EVALUATION_TELEMETRY_VERSION, Map.of(), Map.of(), List.of(),
                    new GroundingSummary(0, 0, 0), CandidateFixture.empty(), "0".repeat(64));
        }
    }

    public record CandidateFixture(
            String version,
            String taskId,
            String targetRevision,
            String contextDigest,
            String projectionDigest,
            List<CandidateRow> candidates,
            String digest
    ) {
        public CandidateFixture {
            if (!CANDIDATE_FIXTURE_VERSION.equals(version) || candidates == null
                    || candidates.size() > MAXIMUM_CANDIDATE_FIXTURE_ROWS
                    || taskId == null || taskId.isBlank() || targetRevision == null || targetRevision.isBlank()
                    || contextDigest == null || !contextDigest.matches("[0-9a-f]{64}")
                    || projectionDigest == null || !projectionDigest.matches("[0-9a-f]{64}")
                    || digest == null || !digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Candidate fixture identity or bounds are invalid");
            }
            candidates = List.copyOf(candidates);
            if (candidates.stream().map(CandidateRow::reference).distinct().count() != candidates.size()) {
                throw new IllegalArgumentException("Candidate fixture contains duplicate references");
            }
        }

        static CandidateFixture empty() {
            return new CandidateFixture(CANDIDATE_FIXTURE_VERSION, "empty", "UNKNOWN",
                    "0".repeat(64), "0".repeat(64), List.of(),
                    ContextLossLedger.digest(new byte[0]));
        }
    }

    public record CandidateRow(
            String reference,
            String kind,
            String path,
            int score,
            String trust,
            boolean grounded,
            boolean selected,
            boolean projected,
            String reason
    ) {
        public CandidateRow {
            if (reference == null || reference.isBlank() || reference.length() > 512
                    || !CANDIDATE_KINDS.contains(kind)
                    || trust == null || !TELEMETRY_TRUST.contains(trust)
                    || !CANDIDATE_REASONS.contains(reason)
                    || path != null && (path.length() > 160 || !isCanonicalPath(path))
                    || projected && !selected || grounded && !projected) {
                throw new IllegalArgumentException("Candidate fixture row is invalid");
            }
        }
    }

    public record RequestedFileOutcome(
            String path,
            boolean found,
            boolean selected,
            boolean projected,
            boolean grounded,
            String absenceReason
    ) {
        public RequestedFileOutcome {
            if (path == null || path.length() > 160 || !isCanonicalPath(path)) {
                throw new IllegalArgumentException("Requested file path is invalid");
            }
            if (absenceReason != null && absenceReason.length() > 80) {
                throw new IllegalArgumentException("Requested file absence reason is invalid");
            }
            if (selected && !found || projected && !selected || grounded && !projected
                    || (absenceReason == null) != (grounded)) {
                throw new IllegalArgumentException("Requested file outcome is inconsistent");
            }
        }
    }

    public record GroundingSummary(
            long authorizedCount,
            long projectedCount,
            long projectedGroundedCount
    ) {
        public GroundingSummary {
            if (authorizedCount < 0 || projectedCount < 0 || projectedGroundedCount < 0) {
                throw new IllegalArgumentException("Grounding summary counts must be non-negative");
            }
        }
    }
}
