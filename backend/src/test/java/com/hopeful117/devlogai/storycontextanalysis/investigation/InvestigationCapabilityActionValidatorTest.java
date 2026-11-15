package com.hopeful117.devlogai.storycontextanalysis.investigation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationCapabilityActionValidatorTest {
    private static final Map<InvestigationActionKind, InvestigationCapability> CAPABILITIES =
            InvestigationCapabilityRegistry.phaseOne();
    private static final InvestigationScope SCOPE = new InvestigationScope(
            "devlog", "revision-1", java.util.List.of("backend", "docs"));
    private static final InvestigationBudget BUDGET = new InvestigationBudget(
            4, 3, 10, 100_000, 10_000, 60_000, 4_000);

    @Test
    void acceptsBoundedSearchWithinAuthorizedScope() {
        var result = validate(new SearchEvidenceRequest(
                "action-1", "devlog", "revision-1", "find the validator", "validator",
                "backend", 10, 10_000, 2_000));

        assertTrue(result.accepted());
    }

    @Test
    void acceptsBoundedEvidenceRange() {
        var result = validate(new ReadEvidenceRangeRequest(
                "action-1", "devlog", "revision-1", "inspect the validator", "backend/src/Main.java",
                10, 20, 1, 10_000, 2_000));

        assertTrue(result.accepted());
    }

    @Test
    void rejectsUnknownCapabilityBeforeRetrieval() {
        var result = InvestigationCapabilityActionValidator.validate(
                action("unknown"), SCOPE, BUDGET, InvestigationUsage.empty(), Set.of(), Map.of());

        assertRejected(result, "UNKNOWN_CAPABILITY");
    }

    @Test
    void rejectsProjectRevisionAndPathViolations() {
        assertRejected(validate(new SearchEvidenceRequest("a", "other", "revision-1", "purpose",
                "query", "backend", 1, 1, 1)), "PROJECT_OUT_OF_SCOPE");
        assertRejected(validate(new SearchEvidenceRequest("b", "devlog", "revision-2", "purpose",
                "query", "backend", 1, 1, 1)), "REVISION_MISMATCH");
        assertRejected(validate(new SearchEvidenceRequest("c", "devlog", "revision-1", "purpose",
                "query", "outside", 1, 1, 1)), "PATH_OUT_OF_SCOPE");
    }

    @Test
    void rejectsMalformedLocatorsAndCapabilityLimits() {
        assertRejected(validate(new ReadEvidenceRangeRequest("a", "devlog", "revision-1", "purpose",
                "../secret", 1, 2, 1, 1, 1)), "INVALID_LOCATOR");
        assertRejected(validate(new ReadEvidenceRangeRequest("b", "devlog", "revision-1", "purpose",
                "backend/file", 0, 2, 1, 1, 1)), "INVALID_LOCATOR");
        assertRejected(validate(new SearchEvidenceRequest("c", "devlog", "revision-1", "purpose",
                "query", "backend", 21, 1, 1)), "CAPABILITY_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsDuplicateActionsAndExhaustedBudgets() {
        var duplicate = InvestigationCapabilityActionValidator.validate(
                action("a"), SCOPE, BUDGET, InvestigationUsage.empty(), Set.of("a"), CAPABILITIES);
        assertRejected(duplicate, "DUPLICATE_ACTION");

        var exhausted = InvestigationCapabilityActionValidator.validate(
                action("b"), SCOPE, BUDGET,
                new InvestigationUsage(4, 0, 0, 0, 0, 0, 0), Set.of(), CAPABILITIES);
        assertRejected(exhausted, "BUDGET_EXHAUSTED");

        var providerOutputExhausted = InvestigationCapabilityActionValidator.validate(
                action("c"), SCOPE, BUDGET,
                new InvestigationUsage(0, 0, 0, 0, 0, 0, 4_000), Set.of(), CAPABILITIES);
        assertRejected(providerOutputExhausted, "BUDGET_EXHAUSTED");
    }

    @Test
    void matchesPythonActionIdentifierAndPurposeBounds() {
        assertRejected(validate(action("bad id")), "DUPLICATE_ACTION");
        assertRejected(validate(new SearchEvidenceRequest("a".repeat(101), "devlog", "revision-1",
                "purpose", "query", "backend", 1, 1, 1)), "DUPLICATE_ACTION");
        assertRejected(validate(new SearchEvidenceRequest("valid", "devlog", "revision-1",
                "x".repeat(501), "query", "backend", 1, 1, 1)), "INVALID_PURPOSE");
    }

    @Test
    void validationDoesNotMutateInputs() {
        var actionIds = new java.util.HashSet<>(Set.of("existing"));
        validate(action("new"), SCOPE, BUDGET, InvestigationUsage.empty(), actionIds);

        assertEquals(Set.of("existing"), actionIds);
    }

    private static InvestigationActionValidation validate(InvestigationAction action) {
        return validate(action, SCOPE, BUDGET, InvestigationUsage.empty(), Set.of());
    }

    private static InvestigationActionValidation validate(InvestigationAction action,
                                                          InvestigationScope scope,
                                                          InvestigationBudget budget,
                                                          InvestigationUsage usage,
                                                          Set<String> actionIds) {
        return InvestigationCapabilityActionValidator.validate(
                action, scope, budget, usage, actionIds, CAPABILITIES);
    }

    private static SearchEvidenceRequest action(String id) {
        return new SearchEvidenceRequest(id, "devlog", "revision-1", "purpose", "query",
                "backend", 1, 1, 1);
    }

    private static void assertRejected(InvestigationActionValidation result, String code) {
        assertTrue(!result.accepted());
        assertEquals(code, result.code());
    }
}
