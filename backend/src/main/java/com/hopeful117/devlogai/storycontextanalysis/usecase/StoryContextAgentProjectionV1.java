package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.engineeringcontext.CanonicalContextDigest;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

public final class StoryContextAgentProjectionV1 {
    public static final String CONTRACT_VERSION = "story-context-agent-projection/v1";
    public static final String PROJECTION_VERSION = "sca/v1";

    private StoryContextAgentProjectionV1() { }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> build(CanonicalEngineeringContext canonical,
            String projectSlug, UUID storyId, String intent, List<String> files,
            EngineeringStory story, ObjectMapper mapper) {
        Objects.requireNonNull(canonical, "canonical context");
        if (projectSlug == null || projectSlug.isBlank() || intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("projectSlug and intent are required");
        }
        if (files != null && files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("files must not contain null");
        }
        if (story != null && story.getProject() != null && story.getProject().getSlug() != null
                && !projectSlug.equals(story.getProject().getSlug())) {
            throw new IllegalArgumentException("story does not belong to project");
        }
        List<String> canonicalFiles = files == null ? List.of() : List.copyOf(files);
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("projectSlug", projectSlug);
        request.put("storyId", storyId == null ? null : storyId.toString());
        request.put("intent", intent);
        request.put("files", canonicalFiles);
        if (canonical.requestEcho() != null) {
            if (!Objects.equals(projectSlug, canonical.requestEcho().projectSlug())
                    || !Objects.equals(intent, canonical.requestEcho().intent())
                    || !Objects.equals(storyId, canonical.requestEcho().storyId())
                    || !Objects.equals(canonicalFiles, canonical.requestEcho().files())) {
                throw new IllegalArgumentException("projection request does not match canonical request echo");
            }
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("contractVersion", CONTRACT_VERSION);
        result.put("projectionVersion", PROJECTION_VERSION);
        result.put("contextDigest", requireDigest(canonical.contextDigest(), "contextDigest"));
        result.put("request", request);
        result.put("requestEcho", new LinkedHashMap<>(request));
        result.put("scope", new LinkedHashMap<>(request));
        result.put("freshness", freshness(canonical, projectSlug));
        result.put("context", context(canonical, mapper));
        result.put("groundingCandidates", grounding(canonical));
        result.put("accounting", accounting(canonical));
        result.put("policy", Map.of("compositionVersion",
                canonical.contextVersion() == null ? "unknown" : canonical.contextVersion(),
                "projectionVersion", PROJECTION_VERSION));
        result.put("projectionDigest", projectionDigest(result, mapper));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String,Object> context(CanonicalEngineeringContext c, ObjectMapper m) {
        Map<String,Object> x = new LinkedHashMap<>();
        x.put("project", m.convertValue(c.projection().project(), Map.class));
        x.put("sections", c.projection().sections() == null ? List.of() : c.projection().sections());
        x.put("repositoryEvidence", c.repositoryContext() == null || c.repositoryContext().evidence() == null
                ? List.of() : c.repositoryContext().evidence().stream()
                .map(evidence -> m.convertValue(evidence, Map.class)).toList());
        x.put("relations", c.relationsByReference());
        return x;
    }

    private static Map<String,Object> freshness(CanonicalEngineeringContext c, String project) {
        String revision = canonicalRevision(c);
        String state = String.valueOf(c.freshness().getOrDefault("state",
                c.freshness().getOrDefault("status", "UNKNOWN"))).toUpperCase(Locale.ROOT);
        if (!Set.of("FRESH","STALE","UNKNOWN","NOT_ESTABLISHED").contains(state)) state = "UNKNOWN";
        return Map.of("sourceRevision", Map.of("kind", "PROJECT_REVISION",
                "project", project, "revision", revision), "state", state);
    }

    /** Returns the sole revision owning the complete evidence snapshot. */
    static String canonicalRevision(CanonicalEngineeringContext c) {
        Set<String> revisions = new LinkedHashSet<>();
        if (c.repositoryContext() != null) for (var evidence : c.repositoryContext().evidence()) {
            if (evidence.content() != null) addRevision(revisions, evidence.content().revision());
        }
        Object rawFreshness = c.freshness().get("sourceRevision");
        if (rawFreshness instanceof Map<?, ?> source) {
            Object revision = source.get("revision");
            if (revision != null && !(revision instanceof String)) {
                throw new IllegalArgumentException("PROJECT_REVISION identity is invalid");
            }
            addRevision(revisions, (String) revision);
        }
        if (revisions.isEmpty()) throw new IllegalArgumentException("PROJECT_REVISION identity is missing");
        if (revisions.size() != 1) throw new IllegalArgumentException("Evidence snapshot contains mixed project revisions");
        return revisions.iterator().next();
    }

    private static void addRevision(Set<String> revisions, String revision) {
        if (revision == null || revision.isBlank()) return;
        if (Set.of("UNKNOWN", "UNSPECIFIED").contains(revision)) {
            throw new IllegalArgumentException("PROJECT_REVISION identity is not resolvable");
        }
        revisions.add(revision);
    }

    private static Map<String,Object> grounding(CanonicalEngineeringContext c) {
        if (c.repositoryContext() == null) throw new IllegalArgumentException("grounding evidence snapshot is required");
        Map<String, com.hopeful117.devlogai.repositorycontext.RepositoryEvidence> evidence = c.repositoryContext().evidence().stream()
                .filter(e -> e.reference() != null).collect(java.util.stream.Collectors.toMap(
                        com.hopeful117.devlogai.repositorycontext.RepositoryEvidence::reference, e -> e, (a,b) -> a));
        List<Map<String,Object>> values = c.authorizedReferences().stream().map(ref -> {
            var item = evidence.get(ref.reference());
            if (item == null || item.provenance() == null || c.provenanceByReference().get(ref.reference()) == null
                    || !Objects.equals(item.provenance(), c.provenanceByReference().get(ref.reference())))
                throw new IllegalArgumentException("grounding reference is not present in the evidence snapshot");
            String trust = c.trustByReference().get(ref.reference());
            if (c.provenanceByReference().get(ref.reference()) == null || trust == null || trust.isBlank()) {
                throw new IllegalArgumentException("grounding provenance and trust are required");
            }
            // ADR-068: typed references contain exactly type/ref/scope.
            // Project and revision identify freshness.sourceRevision only.
            Map<String,Object> typed = Map.of("type", "REPOSITORY_EVIDENCE",
                    "ref", ref.reference(), "scope", "PROJECT_REVISION");
            Map<String,Object> source = new LinkedHashMap<>();
            source.put("resource", ref.resource() == null ? ref.reference() : ref.resource());
            source.put("provenance", c.provenanceByReference().get(ref.reference()));
            source.put("revision", canonicalRevision(c));
            return Map.of("reference", typed, "source", source,
                    "trust", trust);
        }).toList();
        return Map.of("repositoryEvidence", values);
    }

    private static Map<String,Object> accounting(CanonicalEngineeringContext c) {
        Map<String,Object> a = new LinkedHashMap<>();
        Map<String,Object> raw = c.accounting();
        int candidates = number(raw, "candidateCount");
        int selected = number(raw, "selectedCount");
        int discarded = number(raw, "discardedCount");
        int used = number(raw, "usedTokens");
        int budget = number(raw, "budget");
        a.put("candidateCount", candidates); a.put("selectedCount", selected);
        a.put("discardedCount", discarded); a.put("usedTokens", used);
        if (candidates < 0 || selected < 0 || discarded < 0 || used < 0 || budget < 0) throw new IllegalArgumentException("Accounting values must be non-negative");
        if (used > budget) throw new IllegalArgumentException("Accounting usedTokens exceeds budget");
        a.put("budget", budget); a.put("truncated",
                c.repositoryContext() != null && c.repositoryContext().truncated());
        List<Map<String, String>> warnings = c.repositoryContext() == null ? List.of() : c.repositoryContext().warnings().stream().map(value -> Map.of("code", "CONTEXT_WARNING", "message", value)).sorted(Comparator.comparing((Map<String, String> value) -> value.get("code")).thenComparing(value -> value.get("message"))).toList();
        a.put("warnings", warnings);
        return a;
    }

    private static int number(Map<String,Object> raw, String key) {
        if (raw == null || !raw.containsKey(key) || raw.get(key) == null) throw new IllegalArgumentException("Accounting value is required: " + key);
        Object value = raw.get(key);
        if (!(value instanceof Number n) || value instanceof Float || value instanceof Double)
            throw new IllegalArgumentException("Accounting value must be a non-negative representable integer: " + key);
        try {
            java.math.BigInteger integer = new java.math.BigDecimal(n.toString()).toBigIntegerExact();
            if (integer.signum() < 0 || integer.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
                throw new IllegalArgumentException("Accounting value must be a non-negative representable integer: " + key);
            return integer.intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("Accounting value must be a non-negative representable integer: " + key);
        }
    }

    private static String requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        return value;
    }

    private static String projectionDigest(Map<String,Object> result, ObjectMapper mapper) {
        Map<String,Object> identity = new LinkedHashMap<>(result);
        identity.remove("projectionDigest");
        return CanonicalContextDigest.calculate(identity);
    }
}
