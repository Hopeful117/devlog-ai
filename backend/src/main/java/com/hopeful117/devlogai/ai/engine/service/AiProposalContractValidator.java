package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiProposalResult;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
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
            if (proposal.type() == ProposalType.ENGINEERING_EVENT) {
                if (!allowedFactIds.containsAll(proposal.supportingFactIds()))
                    fail("Supporting Fact IDs must exist in selected knowledge");
                if (!allowedObservationIds.containsAll(proposal.supportingObservationIds()))
                    fail("Supporting Observation IDs must exist in selected knowledge");
                validateEvent(proposal, duplicates);
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

    private String text(Map<String, Object> payload, String key, int maximum) {
        Object value = payload.get(key);
        if (!(value instanceof String text) || text.isBlank()
                || text.codePointCount(0, text.length()) > maximum)
            fail("Engineering Event " + key + " is invalid");
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
