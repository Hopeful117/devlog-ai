package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Core-owned compatibility projection; it performs no retrieval or selection. */
public final class StoryContextAgentProjection {
    public static final String PROJECTION_VERSION = "sca-prompt-projection-v2";

    private StoryContextAgentProjection() {
    }

    public static Map<String, Object> project(CanonicalEngineeringContext canonical,
                                               EngineeringStory story,
                                               ObjectMapper objectMapper) {
        Map<String, Object> projection = new LinkedHashMap<>();
        var engineeringContext = canonical.projection();
        var repositoryContext = canonical.repositoryContext();
        projection.put("project", value(objectMapper, engineeringContext.project()));
        projection.put("analysis", Map.of("intent", engineeringContext.intent()));
        projection.put("projectProfile", repositoryContext == null
                ? Map.of() : value(objectMapper, repositoryContext.profile()));
        // Deterministic, non-broadening views of evidence already selected by
        // Core. No retrieval, ranking, or semantic selection is performed.
        projection.put("selectedFacts", authorizedEvidence(engineeringContext.evidence(), canonical, objectMapper, "FACT"));
        projection.put("selectedObservations", authorizedEvidence(engineeringContext.evidence(), canonical, objectMapper, "OBSERVATION"));
        projection.put("diagnostics", value(objectMapper, canonical.diagnostics()));
        projection.put("selectedInsights", authorizedEvidence(engineeringContext.evidence(), canonical, objectMapper, "INSIGHT"));
        projection.put("selectionMetadata", selectionMetadata(canonical));
        projection.put("repositoryContext", value(objectMapper, repositoryContext));
        projection.put("engineeringStories", List.of(storyMap(story)));
        // Preserve rich Core authority under one clearly named envelope.
        projection.put("canonicalContext", value(objectMapper, canonical));
        return projection;
    }

    private static List<Map<String, Object>> authorizedEvidence(
            List<EngineeringEvidence> evidence, CanonicalEngineeringContext canonical,
            ObjectMapper objectMapper, String kind) {
        var authorized = canonical.authorizedReferences().stream()
                .map(ref -> ref.reference()).collect(java.util.stream.Collectors.toSet());
        return evidence.stream()
                .filter(item -> kind.equalsIgnoreCase(item.kind()))
                .filter(item -> item.reference() != null && authorized.contains(item.reference()))
                .map(item -> value(objectMapper, item))
                .toList();
    }

    private static Map<String, Object> selectionMetadata(CanonicalEngineeringContext canonical) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projectionVersion", PROJECTION_VERSION);
        metadata.put("contextVersion", canonical.contextVersion());
        metadata.put("contextDigest", canonical.contextDigest());
        metadata.put("freshness", canonical.freshness());
        metadata.put("accounting", canonical.accounting());
        return metadata;
    }

    private static Map<String, Object> storyMap(EngineeringStory story) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", story.getId().toString());
        result.put("storyNumber", story.getStoryNumber());
        result.put("title", story.getTitle());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> value(ObjectMapper objectMapper, Object value) {
        return value == null ? Map.of() : objectMapper.convertValue(value, Map.class);
    }
}
