package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Evaluation-only bridge; it never persists a task or analysis. */
@Tag("evaluation")
class CoreV2EvaluationBridgeTest {

    @Test
    @SuppressWarnings("unchecked")
    void validateCaptureThroughCore() throws Exception {
        String inputPath = System.getProperty("story0132.bridge.input");
        String outputPath = System.getProperty("story0132.bridge.output");
        if (inputPath == null || outputPath == null) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> input = mapper.readValue(Files.readString(Path.of(inputPath)), Map.class);
        List<Map<String, Object>> items = (List<Map<String, Object>>) input.getOrDefault("items", List.of());
        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("index", item.get("index"));
            try {
                Map<String, Object> selectedKnowledge = (Map<String, Object>) item.get("selectedKnowledge");
                Map<String, Object> groundingContract = (Map<String, Object>) item.get("groundingContract");
                String contextDigest = (String) item.get("contextDigest");
                Map<String, Object> resultMap = canonicalizeConfidence(
                        (Map<String, Object>) item.get("result"));
                record.put("deterministicTransformation", "confidence.level_to_enum_string");

                AiTask task = AiTask.builder()
                        .id(UUID.randomUUID())
                        .contextDigest(contextDigest)
                        .contextSnapshot(Map.of("groundingContract", groundingContract))
                        .selectedKnowledgeSnapshot(selectedKnowledge)
                        .aiReferenceMappingSnapshot(referenceMapping(selectedKnowledge))
                        .build();
                StoryContextAnalysisResult result = mapper.convertValue(resultMap, StoryContextAnalysisResult.class);
                StoryContextAnalysisResult bound =
                        AnalyzeStoryContextUseCase.validateStoryContextAnalysisResult(result, task);
                record.put("ok", true);
                record.put("result", mapper.convertValue(bound, Map.class));
            } catch (RuntimeException exception) {
                record.put("ok", false);
                record.put("error", exception.getMessage());
            }
            output.add(record);
        }
        Files.writeString(Path.of(outputPath), mapper.writeValueAsString(Map.of("items", output)));
    }

    private Map<String, Object> referenceMapping(Map<String, Object> selectedKnowledge) {
        Map<String, Object> repositoryContext = (Map<String, Object>) selectedKnowledge.get("repositoryContext");
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) repositoryContext.get("evidence");
        List<Map<String, Object>> bindings = evidence.stream().map(item -> Map.of(
                "type", AiReferenceType.REPOSITORY_EVIDENCE.name(),
                "ref", item.get("reference"),
                "scope", AiReferenceScope.REPOSITORY.name(),
                "canonicalSourceIdentity", item.get("reference"),
                "groundingCapabilities", List.of("EVIDENCE_REFERENCE")
        )).toList();
        return Map.of(
                "contractVersion", "story0132-v2-evaluation",
                "mappingDigest", "0".repeat(64),
                "bindings", bindings,
                "architectureKnowledgeReferences", List.of()
        );
    }

    private Map<String, Object> canonicalizeConfidence(Map<String, Object> result) {
        Map<String, Object> canonical = new LinkedHashMap<>(result);
        Object confidence = canonical.get("confidence");
        if (confidence instanceof Map<?, ?> confidenceObject
                && confidenceObject.get("level") instanceof String level) {
            canonical.put("confidence", level);
        }
        Object outputClassification = canonical.get("outputClassification");
        if (outputClassification instanceof List<?> entries) {
            canonical.put("outputClassification", Map.of("entries", entries));
        }
        return canonical;
    }
}
