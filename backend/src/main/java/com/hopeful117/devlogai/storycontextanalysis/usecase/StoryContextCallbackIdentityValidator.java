package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.dto.PromptExecutionMetadata;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.storycontextagent.StoryContextAgentProtocolV1;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates callback identities issued by Core against the immutable task snapshot. */
@Service
public class StoryContextCallbackIdentityValidator {
    public void validate(AiTask task, PromptExecutionMetadata metadata) {
        if (metadata == null) {
            throw new InvalidAiTaskResultException("Story Context Analysis callback is missing prompt identities");
        }
        if (!StoryContextAgentProtocolV1.PROTOCOL_VERSION.equals(metadata.protocolVersion())) {
            throw new InvalidAiTaskResultException("Story Context Analysis callback protocolVersion is missing or unsupported");
        }
        Map<String, Object> snapshot = task.getContextSnapshot() == null ? Map.of() : task.getContextSnapshot();
        String expectedContext = task.getContextDigest();
        String expectedProjection = task.getProjectionDigest();
        String expectedSelection = task.getSelectionDigest();
        String expectedProjectionVersion = String.valueOf(snapshot.get("projectionVersion"));
        Object expectedScope = snapshot.get("scope");
        Object expectedFreshness = snapshot.get("freshness");
        Object expectedGrounding = snapshot.get("groundingDigest");
        if (expectedProjection == null || !expectedProjection.matches("[0-9a-f]{64}")) {
            throw new InvalidAiTaskResultException("Story Context Analysis task is missing a valid projection digest");
        }
        if (metadata.projectionDigest() == null
                || !metadata.projectionDigest().matches("[0-9a-f]{64}")) {
            throw new InvalidAiTaskResultException("Story Context Analysis callback is missing a valid projection digest");
        }
        if (!Objects.equals(expectedContext, metadata.contextDigest())) {
            throw new InvalidAiTaskResultException("Context digest mismatch in callback");
        }
        if (!Objects.equals(expectedProjection, metadata.projectionDigest())) {
            throw new InvalidAiTaskResultException("Projection digest mismatch in callback");
        }
        if (!Objects.equals(expectedSelection, metadata.selectionDigest())) {
            throw new InvalidAiTaskResultException("Selection digest mismatch in callback");
        }
        if (!Objects.equals(expectedProjectionVersion, metadata.projectionVersion())
                || !Set.of(StoryContextAgentProjectionV1.PROJECTION_VERSION,
                StoryContextAgentProjectionV1.V2_PROJECTION_VERSION).contains(expectedProjectionVersion)) {
            throw new InvalidAiTaskResultException("Projection version mismatch in callback");
        }
        if (!(metadata.scope() instanceof Map<?, ?>) || !Objects.equals(expectedScope, metadata.scope())) {
            throw new InvalidAiTaskResultException("Scope mismatch in callback");
        }
        if (!(metadata.freshness() instanceof Map<?, ?>) || !Objects.equals(expectedFreshness, metadata.freshness())) {
            throw new InvalidAiTaskResultException("Freshness mismatch in callback");
        }
        if (!(expectedGrounding instanceof String grounding) || !grounding.matches("[0-9a-f]{64}")
                || !Objects.equals(grounding, metadata.groundingDigest())) {
            throw new InvalidAiTaskResultException("Grounding identity mismatch in callback");
        }
        boolean hasIdentitySnapshot = snapshot.containsKey("contextDigest")
                || snapshot.containsKey("projectionDigest") || snapshot.containsKey("selectionDigest");
        if (hasIdentitySnapshot && (!Objects.equals(expectedContext, snapshot.get("contextDigest"))
                || !Objects.equals(expectedProjection, snapshot.get("projectionDigest"))
                || !Objects.equals(expectedSelection, snapshot.get("selectionDigest")))) {
            throw new InvalidAiTaskResultException("AI task snapshot identity is incomplete or inconsistent");
        }
        validateSnapshotScope(task);
        if (!Objects.equals(expectedProjectionVersion, snapshot.get("projectionVersion"))) {
            throw new InvalidAiTaskResultException("AI task snapshot projection version is missing or inconsistent");
        }
        if (!(snapshot.get("freshness") instanceof Map<?, ?>)
                || !(snapshot.get("groundingContract") instanceof Map<?, ?>)) {
            throw new InvalidAiTaskResultException("AI task snapshot freshness and grounding identity are required");
        }
        Object projection = snapshot.get("projection");
        if (!(projection instanceof Map<?, ?> p)
                || !Objects.equals(expectedContext, p.get("contextDigest"))
                || !Objects.equals(expectedProjection, p.get("projectionDigest"))
                || !Objects.equals(expectedProjectionVersion, p.get("projectionVersion"))) {
            throw new InvalidAiTaskResultException("AI task snapshot projection identity is incomplete or inconsistent");
        }
    }

    private static void validateSnapshotScope(AiTask task) {
        Object raw = taskSnapshotScope(task);
        if (!(raw instanceof Map<?, ?> scope)) throw new IllegalStateException("AI task snapshot scope is missing");
        Set<String> keys = Set.of("projectSlug", "storyId", "intent", "files", "question");
        if (!scope.keySet().stream().allMatch(keys::contains)
                || scope.get("projectSlug") == null || scope.get("files") == null) {
            throw new IllegalStateException("AI task snapshot scope is incomplete or inconsistent");
        }
        if ((scope.containsKey("intent") && !Objects.equals(task.getIntentId(), scope.get("intent")))
                || !Objects.equals(task.getContextSnapshot().get("storyId"), scope.get("storyId"))) {
            throw new IllegalStateException("AI task scope does not match its intent or story");
        }
        Object projection = task.getSelectedKnowledgeSnapshot();
        if (projection instanceof Map<?, ?> p && p.get("request") instanceof Map<?, ?> request
                && !Objects.equals(scope, request)) {
            throw new IllegalStateException("AI task snapshot scope does not match request scope");
        }
    }

    private static Object taskSnapshotScope(AiTask task) {
        return task.getContextSnapshot() == null ? null : task.getContextSnapshot().get("scope");
    }
}
