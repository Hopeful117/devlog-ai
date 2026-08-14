package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiProposalResult;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
class AiProposalContractValidator {
    private static final Set<String> EVENT_FIELDS = Set.of(
            "schemaVersion", "category", "title", "summary", "significance");
    private static final Set<String> INSIGHT_FIELDS = Set.of(
            "insightType", "title", "summary", "rationale", "deltaType", "targetInsightId");
    private static final Set<String> INSIGHT_BASE_FIELDS = Set.of(
            "insightType", "title", "summary", "rationale");
    private static final Set<String> INSIGHT_NEW_FIELDS = Set.of(
            "insightType", "title", "summary", "rationale", "deltaType");
    private final IntentCatalog intents;

    void validate(AiTask task, List<AiProposalResult> proposals) {
        var intent = intents.resolve(task.getIntentId(), task.getIntentVersion());
        if (proposals.size() > 10) fail("Proposal count exceeds Intent maximum");
        Set<String> allowedReferences = new LinkedHashSet<>();
        Set<UUID> allowedFactIds = new LinkedHashSet<>();
        Set<UUID> allowedObservationIds = new LinkedHashSet<>();
        collectReferences(task.getSelectedKnowledgeSnapshot(), allowedReferences);
        collectIds(task.getSelectedKnowledgeSnapshot(), "selectedFacts", allowedFactIds);
        collectIds(task.getSelectedKnowledgeSnapshot(), "selectedObservations", allowedObservationIds);
        Set<String> duplicates = new HashSet<>();
        for (AiProposalResult proposal : proposals) {
            if (proposal.type() != intent.outputProposalType())
                fail("Proposal type does not match Intent outputProposalType");
            if (!allowedReferences.containsAll(proposal.evidenceReferences()))
                fail("Evidence references must exist in selected knowledge");
            if (!allowedFactIds.containsAll(proposal.supportingFactIds()))
                fail("Supporting Fact IDs must exist in selected knowledge");
            if (!allowedObservationIds.containsAll(proposal.supportingObservationIds()))
                fail("Supporting Observation IDs must exist in selected knowledge");
            if (proposal.type() == ProposalType.ENGINEERING_EVENT) {
                validateEvent(proposal, duplicates);
            } else if (proposal.type() == ProposalType.INSIGHT) {
                validateInsight(task, intent, proposal);
            }
        }
    }

    private void collectIds(Object value, String collectionName, Set<UUID> result) {
        if (!(value instanceof Map<?, ?> map)) return;
        Object collection = map.get(collectionName);
        if (!(collection instanceof List<?> list)) return;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            Object id = entry.get("id");
            try { if (id != null) result.add(UUID.fromString(id.toString())); }
            catch (IllegalArgumentException ignored) { /* invalid selected IDs cannot authorize output */ }
        }
    }

    private void validateEvent(AiProposalResult proposal, Set<String> duplicates) {
        Map<String, Object> payload = proposal.payload();
        if (!payload.keySet().equals(EVENT_FIELDS)) fail("Engineering Event payload fields are invalid");
        if (!"engineering-event-proposal-v1".equals(payload.get("schemaVersion")))
            fail("Engineering Event schemaVersion is invalid");
        String category = text(payload, "category", 50);
        try { EngineeringEventCategory.valueOf(category); }
        catch (IllegalArgumentException invalid) { fail("Engineering Event category is invalid"); }
        String title = text(payload, "title", 255);
        text(payload, "summary", 5000);
        text(payload, "significance", 5000);
        if (proposal.supportingFactIds().isEmpty()
                && proposal.supportingObservationIds().isEmpty()
                && proposal.evidenceReferences().isEmpty()) fail("Engineering Event requires grounding");
        if (!duplicates.add(category + "\u0000" + title.toLowerCase(Locale.ROOT)))
            fail("Duplicate Engineering Event proposal");
    }

    private void validateInsight(AiTask task, com.hopeful117.devlogai.intent.model.IntentDefinition intent,
                                 AiProposalResult proposal) {
        Map<String, Object> payload = proposal.payload();
        if (!payload.keySet().equals(INSIGHT_BASE_FIELDS)
                && !payload.keySet().equals(INSIGHT_NEW_FIELDS)
                && !payload.keySet().equals(INSIGHT_FIELDS)) {
            fail("Insight payload fields are invalid");
        }
        String insightType = text(payload, "insightType", 100);
        try {
            InsightType.valueOf(insightType);
        } catch (IllegalArgumentException invalid) {
            fail("Insight insightType is invalid");
        }
        if (!intent.supportedInsightTypes().contains(InsightType.valueOf(insightType))) {
            fail("Insight insightType is not supported by Intent");
        }
        text(payload, "title", 255);
        text(payload, "summary", 5000);
        text(payload, "rationale", 5000);
        if ("architecture-overview".equals(intent.id())) {
            validateArchitectureDelta(task, payload);
        }
    }

    private void validateArchitectureDelta(AiTask task, Map<String, Object> payload) {
        String deltaType = text(payload, "deltaType", 50);
        if (!Set.of("NEW", "ENRICHES", "SUPERSEDES").contains(deltaType)) {
            fail("Architecture Insight deltaType is invalid");
        }
        Object target = payload.get("targetInsightId");
        if ("NEW".equals(deltaType)) {
            if (target != null) fail("Architecture Insight NEW must not define targetInsightId");
            return;
        }
        if (!(target instanceof String)) {
            fail("Architecture Insight " + deltaType + " requires targetInsightId");
        }
        String targetText = ((String) target).trim();
        if (targetText.isBlank()) fail("Architecture Insight " + deltaType + " requires targetInsightId");
        UUID targetId;
        try {
            targetId = UUID.fromString(targetText);
        } catch (IllegalArgumentException invalid) {
            fail("Architecture Insight targetInsightId is invalid");
            return;
        }
        Set<UUID> allowedTargets = collectArchitectureKnowledgeIds(task.getSelectedKnowledgeSnapshot());
        if (!allowedTargets.contains(targetId)) {
            fail("Architecture Insight targetInsightId must exist in selected existing architecture knowledge");
        }
    }

    private Set<UUID> collectArchitectureKnowledgeIds(Object value) {
        Set<UUID> result = new LinkedHashSet<>();
        if (!(value instanceof Map<?, ?> map)) return result;
        Object collection = map.get("existingArchitectureKnowledge");
        if (!(collection instanceof List<?> list)) return result;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            Object id = entry.get("insightId");
            try {
                if (id != null) result.add(UUID.fromString(id.toString()));
            } catch (IllegalArgumentException ignored) {
                // Invalid selected IDs cannot authorize output.
            }
        }
        return result;
    }

    private String text(Map<String, Object> payload, String key, int maximum) {
        Object value = payload.get(key);
        if (!(value instanceof String text) || text.isBlank()
                || text.codePointCount(0, text.length()) > maximum)
            fail("Proposal field " + key + " is invalid");
        return ((String) value).trim();
    }

    @SuppressWarnings("unchecked")
    private void collectReferences(Object value, Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            Object reference = map.get("reference");
            if (reference instanceof String text) result.add(text);
            for (String key : List.of("evidenceReferences", "relatedReferences")) {
                Object references = map.get(key);
                if (references instanceof List<?> list)
                    list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(result::add);
            }
            map.values().forEach(nested -> collectReferences(nested, result));
        } else if (value instanceof List<?> list) list.forEach(nested -> collectReferences(nested, result));
    }

    private void fail(String message) { throw new InvalidAiTaskResultException(message); }
}
