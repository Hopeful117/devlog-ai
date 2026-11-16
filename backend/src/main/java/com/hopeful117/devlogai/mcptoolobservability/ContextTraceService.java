package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContextTraceService {
    private final McpToolEventRepository eventRepository;
    private final AiTaskRepository aiTaskRepository;

    @Transactional(readOnly = true)
    public ContextTraceView getContextTrace(UUID projectId, String instanceId, String operationId) {
        UUID correlationId = eventRepository.findCorrelationId(projectId, instanceId, operationId);
        if (correlationId == null) {
            throw new EntityNotFoundException("ContextTrace", instanceId + "/" + operationId);
        }
        AiTask task = aiTaskRepository.findById(correlationId)
                .orElseThrow(() -> new EntityNotFoundException("AiTask", correlationId));
        // Cross-project security: reject if the task belongs to a different project
        UUID taskProjectId = task.getAnalysis().getProject().getId();
        if (!projectId.equals(taskProjectId)) {
            throw new EntityNotFoundException("ContextTrace", instanceId + "/" + operationId);
        }

        Map<String, Object> snapshot = task.getContextSnapshot();
        boolean contextAvailable = snapshot != null && !snapshot.isEmpty();

        String projectSlug = null;
        UUID storyId = null;
        String intent = null;
        List<String> files = List.of();
        String freshnessState = null;
        String freshnessRevision = null;
        Map<String, Object> accounting = Map.of();
        List<ContextTraceView.SourceRef> sources = List.of();

        if (contextAvailable) {
            projectSlug = extractString(snapshot, "scope", "projectSlug");
            String storyIdStr = extractString(snapshot, "scope", "storyId");
            if (storyIdStr != null) {
                try { storyId = UUID.fromString(storyIdStr); } catch (IllegalArgumentException ignored) {}
            }
            intent = extractString(snapshot, "scope", "intent");
            Object rawFiles = extractNested(snapshot, "scope", "files");
            if (rawFiles instanceof List<?> list) {
                files = list.stream().filter(v -> v instanceof String).map(v -> (String) v).toList();
            }
            freshnessState = extractString(snapshot, "freshness", "state");
            if (freshnessState == null) freshnessState = extractString(snapshot, "freshness", "status");
            freshnessRevision = extractString(snapshot, "freshness", "sourceRevision", "revision");
            Object rawAccounting = snapshot.get("accounting");
            if (rawAccounting instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") Map<String, Object> acc = (Map<String, Object>) m;
                accounting = acc;
            }
            sources = extractSources(snapshot);
        }

        // Fall back to intentSnapshot for request metadata when contextSnapshot is absent
        if (projectSlug == null && task.getIntentSnapshot() != null) {
            projectSlug = extractString(task.getIntentSnapshot(), "projectSlug");
            intent = extractString(task.getIntentSnapshot(), "intent");
        }

        // Locate the McpOperationView for operation metadata
        List<McpToolEventRepository.McpOperationProjection> ops =
                eventRepository.findOperationsByProject(projectId, 0, 200);
        McpToolEventRepository.McpOperationProjection op = ops.stream()
                .filter(p -> instanceId.equals(p.getInstanceId()) && operationId.equals(p.getOperationId()))
                .findFirst()
                .orElse(null);

        // Synthesis snapshot may carry investigationStopReason
        String investigationStopReason = null;
        if (task.getSynthesisSnapshot() != null) {
            Object r = task.getSynthesisSnapshot().get("investigationStopReason");
            if (r instanceof String s) investigationStopReason = s;
        }

        return new ContextTraceView(
                instanceId,
                operationId,
                op != null ? op.getToolName() : null,
                op != null ? op.getStatus() : task.getStatus().name(),
                op != null ? op.getStartedAt() : task.getCreatedAt(),
                op != null ? op.getCompletedAt() : task.getCompletedAt(),
                op != null ? op.getDurationMs() : null,
                correlationId,
                projectSlug,
                storyId,
                intent,
                files,
                task.getContextDigest(),
                task.getProjectionDigest(),
                freshnessState,
                freshnessRevision,
                sources,
                accounting,
                task.getFailureCode(),
                task.getFailureMessage(),
                investigationStopReason,
                contextAvailable);
    }

    @Transactional(readOnly = true)
    public UUID findProjectIdByAiTaskId(UUID aiTaskId) {
        return aiTaskRepository.findById(aiTaskId)
                .map(t -> t.getAnalysis().getProject().getId())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ContextTraceView getContextTraceByAiTaskId(UUID projectId, UUID aiTaskId) {
        McpToolEventRepository.CorrelationLookupProjection lookup =
                eventRepository.findOpByCorrelationId(projectId, aiTaskId);
        if (lookup == null) {
            throw new EntityNotFoundException("ContextTrace", aiTaskId.toString());
        }
        return getContextTrace(projectId, lookup.getInstanceId(), lookup.getOperationId());
    }

    @SuppressWarnings("unchecked")
    private List<ContextTraceView.SourceRef> extractSources(Map<String, Object> snapshot) {
        List<ContextTraceView.SourceRef> result = new ArrayList<>();
        // canonicalContext.projection.sections
        Object rawCanonical = snapshot.get("canonicalContext");
        Object rawProjection = rawCanonical instanceof Map<?, ?> cc ? ((Map<String, Object>) cc).get("projection") : null;
        if (!(rawProjection instanceof Map<?, ?> ctxMap)) return result;
        // sections: each section has name, trustTier, evidence[]
        Object rawSections = ((Map<String, Object>) ctxMap).get("sections");
        if (rawSections instanceof List<?> sectionList) {
            for (Object rawSection : sectionList) {
                if (!(rawSection instanceof Map<?, ?> section)) continue;
                Map<String, Object> sectionMap = (Map<String, Object>) section;
                String sectionName = str(sectionMap.get("name"));
                if (sectionName == null) sectionName = str(sectionMap.get("trustTier"));
                Object rawEvidence = sectionMap.get("evidence");
                if (!(rawEvidence instanceof List<?> items)) continue;
                for (Object rawItem : items) {
                    if (!(rawItem instanceof Map<?, ?> item)) continue;
                    Map<String, Object> m = (Map<String, Object>) item;
                    String kind = str(m.get("kind"));
                    String id = str(m.get("identifier"));
                    if (id == null) id = str(m.get("reference"));
                    String summary = str(m.get("summary"));
                    result.add(new ContextTraceView.SourceRef(
                            kind != null ? kind : sectionName,
                            id,
                            summary,
                            null));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String extractString(Map<String, Object> map, String... keys) {
        Object current = map;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(keys[i]);
        }
        if (!(current instanceof Map<?, ?> m)) return null;
        Object value = m.get(keys[keys.length - 1]);
        return value instanceof String s ? s : null;
    }

    private static String extractString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }

    private static Object extractNested(Map<String, Object> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(key);
        }
        return current;
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }
}
