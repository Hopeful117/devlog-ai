package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiProposalResult;
import com.hopeful117.devlogai.ai.engine.dto.AnalysisSynthesisResult;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Set<String> DECISION_FIELDS_BASE = Set.of(
            "title", "context", "choice", "rationale");
    private static final Set<String> DECISION_FIELDS_FULL = Set.of(
            "title", "context", "choice", "rationale", "consequences");
    private static final Pattern RELATIONSHIP_KEY_VALUE_PATTERN = Pattern.compile(
            "(?:^|,)\\s*from=([^,\\s]+)\\s*,\\s*to=([^,\\s]+)(?:,|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIONSHIP_ARROW_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_.-])([A-Za-z0-9_.-]+)\\s*(?:->|→)\\s*"
                    + "([A-Za-z0-9_.-]+)(?![A-Za-z0-9_.-])");
    private static final List<String> RELATIONSHIP_KNOWLEDGE_FIELDS = List.of(
            "title", "content", "summary", "rationale");
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
            } else if (proposal.type() == ProposalType.ENGINEERING_DECISION) {
                validateDecision(proposal);
            }
        }
    }

    void validateSynthesis(AiTask task, AnalysisSynthesisResult synthesis, boolean hasDeltas) {
        Set<String> allowedReferences = new LinkedHashSet<>();
        collectReferences(task.getSelectedKnowledgeSnapshot(), allowedReferences);
        collectStringIds(task.getSelectedKnowledgeSnapshot(), "selectedFacts", allowedReferences);
        collectStringIds(task.getSelectedKnowledgeSnapshot(), "selectedObservations", allowedReferences);
        collectSelectedIdentifiers(task.getSelectedKnowledgeSnapshot(), allowedReferences);
        if (!allowedReferences.containsAll(synthesis.groundingReferences())) {
            fail("Synthesis grounding references must exist in selected knowledge");
        }
        if (!hasDeltas && hasUncoveredRelationship(task.getSelectedKnowledgeSnapshot())) {
            fail("An explicit selected component relationship absent from existing architecture "
                    + "knowledge requires an architecture delta proposal");
        }
    }

    private boolean hasUncoveredRelationship(Object value) {
        if (!(value instanceof Map<?, ?> snapshot)) return false;
        Set<Relationship> existingRelationships = existingRelationships(
                snapshot.get("existingArchitectureKnowledge"));
        Object selectedFacts = snapshot.get("selectedFacts");
        if (!(selectedFacts instanceof List<?> facts)) return false;
        for (Object item : facts) {
            if (!(item instanceof Map<?, ?> fact)
                    || !(fact.get("content") instanceof String content)) continue;
            Optional<Relationship> relationship = selectedRelationship(content);
            if (relationship.isPresent()
                    && !existingRelationships.contains(relationship.get())) return true;
        }
        return false;
    }

    private Optional<Relationship> selectedRelationship(String content) {
        Matcher matcher = RELATIONSHIP_KEY_VALUE_PATTERN.matcher(content);
        return matcher.find() ? Optional.of(relationship(matcher)) : Optional.empty();
    }

    private Set<Relationship> existingRelationships(Object value) {
        Set<Relationship> relationships = new LinkedHashSet<>();
        if (!(value instanceof List<?> knowledgeItems)) return relationships;
        for (Object item : knowledgeItems) {
            if (!(item instanceof Map<?, ?> knowledge)) continue;
            for (String field : RELATIONSHIP_KNOWLEDGE_FIELDS) {
                if (!(knowledge.get(field) instanceof String text)) continue;
                collectRelationships(text, RELATIONSHIP_KEY_VALUE_PATTERN, relationships);
                collectRelationships(text, RELATIONSHIP_ARROW_PATTERN, relationships);
            }
        }
        return relationships;
    }

    private void collectRelationships(String text, Pattern pattern,
            Set<Relationship> relationships) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) relationships.add(relationship(matcher));
    }

    private Relationship relationship(Matcher matcher) {
        return new Relationship(
                matcher.group(1).toLowerCase(Locale.ROOT),
                matcher.group(2).toLowerCase(Locale.ROOT));
    }

    private record Relationship(String source, String target) { }

    private void collectSelectedIdentifiers(Object value, Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("id", "insightId")) {
                Object identifier = map.get(key);
                if (identifier != null) {
                    try {
                        result.add(UUID.fromString(identifier.toString()).toString());
                    } catch (IllegalArgumentException ignored) {
                        // Invalid selected identifiers cannot authorize output.
                    }
                }
            }
            map.values().forEach(nested -> collectSelectedIdentifiers(nested, result));
        } else if (value instanceof List<?> list) {
            list.forEach(nested -> collectSelectedIdentifiers(nested, result));
        }
    }

    private void collectStringIds(Object value, String collectionName, Set<String> result) {
        if (!(value instanceof Map<?, ?> map)) return;
        Object collection = map.get(collectionName);
        if (!(collection instanceof List<?> list)) return;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            Object id = entry.get("id");
            if (id != null) result.add(id.toString());
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

    private void validateDecision(AiProposalResult proposal) {
        Map<String, Object> payload = proposal.payload();
        if (!payload.keySet().equals(DECISION_FIELDS_BASE)
                && !payload.keySet().equals(DECISION_FIELDS_FULL)) {
            fail("Engineering Decision payload fields are invalid");
        }
        text(payload, "title", 255);
        text(payload, "context", 5000);
        text(payload, "choice", 5000);
        text(payload, "rationale", 5000);
        if (payload.keySet().equals(DECISION_FIELDS_FULL)) {
            text(payload, "consequences", 5000);
        }
    }

    private void validateArchitectureDelta(AiTask task, Map<String, Object> payload) {
        String deltaType = text(payload, "deltaType", 50);
        if (!Set.of("NEW", "ENRICHES").contains(deltaType)) {
            fail("Architecture Insight deltaType is invalid");
        }
        Object target = payload.get("targetInsightId");
        if ("NEW".equals(deltaType)) {
            if (target != null) fail("Architecture Insight NEW must not define targetInsightId");
            return;
        }
        if (!(target instanceof String)) {
            fail("Architecture Insight ENRICHES requires targetInsightId");
        }
        String targetText = ((String) target).trim();
        if (targetText.isBlank()) fail("Architecture Insight ENRICHES requires targetInsightId");
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
