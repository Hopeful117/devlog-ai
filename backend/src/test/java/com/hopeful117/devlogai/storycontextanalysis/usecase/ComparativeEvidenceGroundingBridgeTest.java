package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceMappingSnapshot;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolutionException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluation-only evidence bridge; it does not construct causal DTOs or persist state. */
@Tag("evaluation")
class ComparativeEvidenceGroundingBridgeTest {
    private static final String BRIDGE_VERSION = "story0135-comparative-java-evidence-bridge-1.0.0";
    private static final String REPOSITORY_ID = "1feead5d-dfc9-4b2c-aa9c-045a8524a9f";
    private static final String REPOSITORY_REVISION = "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149";

    @Test
    @SuppressWarnings("unchecked")
    void resolveComparativeEvidence() throws Exception {
        String inputPath = System.getProperty("story0135.comparative.bridge.input");
        String outputPath = System.getProperty("story0135.comparative.bridge.output");
        if (inputPath == null || outputPath == null) return;

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> input = mapper.readValue(Files.readString(Path.of(inputPath)), Map.class);
        String repositoryId = text(input, "repositoryId");
        String repositoryRevision = text(input, "repositoryRevision");
        if (!REPOSITORY_ID.equals(repositoryId) || !REPOSITORY_REVISION.equals(repositoryRevision)) {
            throw new IllegalArgumentException("comparative repository identity is not frozen");
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> item : (List<Map<String, Object>>) input.getOrDefault("items", List.of())) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("index", item.get("index"));
            try {
                List<Map<String, Object>> evidence = (List<Map<String, Object>>) item.getOrDefault("evidence", List.of());
                if ("ESTABLISHED".equals(item.get("relationshipResult"))
                        && ((List<?>) item.getOrDefault("evidenceCitations", List.of())).isEmpty()) {
                    throw new IllegalStateException("ESTABLISHED comparative answer requires one evidence assertion");
                }
                Map<String, Object> selectedKnowledge = selectedKnowledge(evidence, repositoryRevision);
                AiTask task = AiTask.builder()
                        .contextDigest((String) item.getOrDefault("contextDigest", "comparative-preflight"))
                        .selectedKnowledgeSnapshot(selectedKnowledge)
                        .aiReferenceMappingSnapshot(referenceMapping(evidence))
                        .build();
                TaskSnapshotEvidenceResolver resolver = new TaskSnapshotEvidenceResolver();
                List<Map<String, Object>> resolutions = new ArrayList<>();
                for (Map<String, Object> citation : citations(item)) {
                    resolutions.add(resolver.resolveComparativeCitation(
                            citation, task, repositoryId, repositoryRevision,
                            text(citation, "providerVisibleSourceIdentity")));
                }
                record.put("status", "PASS");
                record.put("groundingContractVersion", "story0135-comparative-grounding-1.0.0");
                record.put("resolutions", resolutions);
            } catch (RuntimeException exception) {
                record.put("status", "FAIL");
                record.put("errorCode", exception instanceof AiReferenceResolutionException reference
                        ? reference.code() : exception.getClass().getSimpleName());
                record.put("diagnostic", exception.getMessage());
            }
            output.add(record);
        }
        Files.writeString(Path.of(outputPath), mapper.writeValueAsString(Map.of(
                "bridgeVersion", BRIDGE_VERSION,
                "groundingContractVersion", "story0135-comparative-grounding-1.0.0",
                "items", output)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> citations(Map<String, Object> item) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll((List<Map<String, Object>>) item.getOrDefault("evidenceCitations", List.of()));
        for (Map<String, Object> claim : (List<Map<String, Object>>) item.getOrDefault("claims", List.of())) {
            for (String reference : (List<String>) claim.getOrDefault("references", List.of())) {
                result.add(Map.of(
                        "reference", reference,
                        "providerVisibleSourceIdentity", "CLAIM_REFERENCE"));
            }
        }
        return result;
    }

    private Map<String, Object> selectedKnowledge(List<Map<String, Object>> evidence, String revision) {
        List<Map<String, Object>> snapshotEvidence = evidence.stream().map(item -> Map.of(
                "reference", item.get("reference"),
                "artifactType", item.getOrDefault("sourceType", "DOCUMENT"),
                "content", Map.of(
                        "revision", revision,
                        "status", "COMPLETE",
                        "text", item.get("content")))).toList();
        return Map.of("repositoryContext", Map.of("evidence", snapshotEvidence));
    }

    private Map<String, Object> referenceMapping(List<Map<String, Object>> evidence) {
        List<Map<String, Object>> bindings = evidence.stream().map(item -> Map.of(
                "type", AiReferenceType.REPOSITORY_EVIDENCE.name(),
                "ref", item.get("reference"),
                "scope", AiReferenceScope.REPOSITORY.name(),
                "canonicalSourceIdentity", item.get("reference"),
                "groundingCapabilities", List.of("EVIDENCE_REFERENCE"))).toList();
        return Map.of(
                "contractVersion", "story0135-comparative-evidence-mapping-1.0.0",
                "mappingDigest", "0".repeat(64),
                "bindings", bindings,
                "architectureKnowledgeReferences", List.of());
    }

    private String text(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing bridge field: " + key);
        }
        return text;
    }
}
