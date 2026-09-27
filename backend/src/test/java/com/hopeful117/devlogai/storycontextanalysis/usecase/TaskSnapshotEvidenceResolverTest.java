package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.reference.*;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaskSnapshotEvidenceResolverTest {
    private static final String REFERENCE = "document:00000000-0000-0000-0000-000000000001:docs/decision.md@rev-1";

    private final TaskSnapshotEvidenceResolver resolver = new TaskSnapshotEvidenceResolver();

    @Test
    void resolvesLineRangeAgainstSelectedSnapshotAndBindsCoreDigest() {
        StoryContextAnalysisResult.CausalAssessment assessment = assessment(
                new StoryContextAnalysisResult.EvidenceLocator(
                        StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 2, 3, null),
                null);

        var bound = resolver.bind(assessment, task());

        assertEquals("Decision\nThis is the authoritative relationship.",
                bound.evidenceAssertions().getFirst().resolvedContent());
        assertNotNull(bound.evidenceAssertions().getFirst().resolvedContentDigest());
    }

    @Test
    void resolvesCanonicalProjectionRepositoryEvidenceForCallbackGrounding() {
        AiTask task = task();
        Map<String, Object> evidence = Map.of(
                "reference", REFERENCE,
                "content", Map.of("status", "COMPLETE", "text", "Canonical projection content", "revision", "rev-1"));
        task.setSelectedKnowledgeSnapshot(Map.of(
                "context", Map.of("repositoryEvidence", List.of(evidence)),
                "groundingContract", Map.of("allowedGroundingReferences", List.of(Map.of(
                        "type", "REPOSITORY_EVIDENCE", "ref", REFERENCE, "scope", "PROJECT_REVISION",
                        "project", "devlog-ai", "revision", "rev-1")))));

        var bound = resolver.bind(assessment(new StoryContextAnalysisResult.EvidenceLocator(
                StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 1, 1, null), null), task);

        assertEquals("Canonical projection content", bound.evidenceAssertions().getFirst().resolvedContent());
    }

    @Test
    void resolvesOnlyTheOriginatingTaskSnapshotContent() {
        var task = task();
        task.setSelectedKnowledgeSnapshot(Map.of(
                "scope", Map.of("projectSlug", "devlog-ai", "revision", "rev-1"),
                "groundingContract", Map.of("allowedGroundingReferences", List.of(Map.of(
                        "type", "REPOSITORY_EVIDENCE", "ref", REFERENCE, "scope", "PROJECT_REVISION",
                        "project", "devlog-ai", "revision", "rev-1")),
                        "allowedEvidenceReferences", List.of(REFERENCE)),
                "repositoryContext", Map.of("evidence", List.of(Map.of(
                        "reference", REFERENCE,
                        "content", Map.of(
                                "status", "COMPLETE",
                                "text", "Snapshot-only content",
                                "revision", "rev-1"))))));

        var assessment = assessment(new StoryContextAnalysisResult.EvidenceLocator(
                StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 1, 1, null), null);

        var bound = resolver.bind(assessment, task);

        assertEquals("Snapshot-only content",
                bound.evidenceAssertions().getFirst().resolvedContent());
    }

    @Test
    void resolvesSectionAndRejectsFabricatedExcerpt() {
        var locator = new StoryContextAnalysisResult.EvidenceLocator(
                StoryContextAnalysisResult.EvidenceLocator.LocatorKind.SECTION, null, null, "Decision");
        var assessment = assessment(locator, "invented");

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, task()));
    }

    @Test
    void rejectsUnknownReferenceBeforeResolvingContent() {
        var assertion = new StoryContextAnalysisResult.EvidenceAssertion(
                new EvidenceRef("unknown", null, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT),
                new StoryContextAnalysisResult.EvidenceLocator(
                        StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 1, 1, null),
                null, null, null, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT);
        var assessment = new StoryContextAnalysisResult.CausalAssessment(
                question(), StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED,
                List.of(assertion), "The requested relationship is not established.");

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, task()));
    }

    @Test
    void evidenceRemovalCannotLeaveAnAuthoritativeAssertionBehind() {
        var assertion = new StoryContextAnalysisResult.EvidenceAssertion(
                new EvidenceRef(REFERENCE, null, EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT),
                new StoryContextAnalysisResult.EvidenceLocator(
                        StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 2, 3, null),
                null, null, null, EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT);
        var assessment = new StoryContextAnalysisResult.CausalAssessment(
                question(), StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED,
                List.of(assertion), "The assertion directly states the relationship.");
        AiTask reduced = task();
        reduced.setSelectedKnowledgeSnapshot(Map.of("repositoryContext", Map.of("evidence", List.of())));

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, reduced));
    }

    @Test
    void rejectsRepositoryWideReferenceForStoryAgentV1() {
        var assessment = assessment(new StoryContextAnalysisResult.EvidenceLocator(
                StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 1, 1, null), null);
        AiTask legacyScoped = task();
        legacyScoped.setAiReferenceMappingSnapshot(AiReferenceMappingSnapshot.from(
                new AiReferenceRegistry(List.of(new AiReferenceBinding(
                        new AiReference(AiReferenceType.REPOSITORY_EVIDENCE, REFERENCE, AiReferenceScope.REPOSITORY),
                        "REPOSITORY_EVIDENCE", REFERENCE, Set.of("EVIDENCE_REFERENCE"))))).asMap());

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, legacyScoped));
    }

    private StoryContextAnalysisResult.CausalAssessment assessment(
            StoryContextAnalysisResult.EvidenceLocator locator, String excerpt) {
        var assertion = new StoryContextAnalysisResult.EvidenceAssertion(
                new EvidenceRef(REFERENCE, null, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT),
                locator, null, null, excerpt, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT);
        return new StoryContextAnalysisResult.CausalAssessment(
                question(), StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED,
                List.of(assertion), "The selected context does not establish the requested relationship.");
    }

    private StoryContextAnalysisResult.CausalQuestion question() {
        return new StoryContextAnalysisResult.CausalQuestion("ADR-043", "ExecutionConfiguration", "CAUSAL", true);
    }

    private AiTask task() {
        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(
                new AiReferenceBinding(
                        new AiReference(AiReferenceType.REPOSITORY_EVIDENCE, REFERENCE, AiReferenceScope.PROJECT_REVISION),
                        "REPOSITORY_EVIDENCE", REFERENCE, Set.of("EVIDENCE_REFERENCE"))));
        Map<String, Object> evidence = Map.of(
                "reference", REFERENCE,
                "content", Map.of(
                        "status", "COMPLETE",
                        "text", "Introduction\nDecision\nThis is the authoritative relationship.\nConclusion",
                        "revision", "rev-1"));
        return taskWithEvidence(List.of(evidence));
    }

    private AiTask taskWithEvidence(List<Map<String, Object>> evidence) {
        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(new AiReferenceBinding(new AiReference(AiReferenceType.REPOSITORY_EVIDENCE, REFERENCE, AiReferenceScope.PROJECT_REVISION), "REPOSITORY_EVIDENCE", REFERENCE, Set.of("EVIDENCE_REFERENCE"))));
        Map<String, Object> typedReference = Map.of(
                "type", "REPOSITORY_EVIDENCE", "ref", REFERENCE,
                "coreReference", REFERENCE, "taskReference", REFERENCE,
                "scope", "PROJECT_REVISION", "project", "devlog-ai", "revision", "rev-1");
        Map<String, Object> contract = Map.of(
                "allowedGroundingReferences", List.of(typedReference),
                "allowedEvidenceReferences", List.of(REFERENCE));
        Map<String, Object> snapshot = Map.of(
                "scope", Map.of("projectSlug", "devlog-ai", "revision", "rev-1"),
                "groundingContract", contract,
                "repositoryContext", Map.of("evidence", evidence));
        return AiTask.builder()
                .selectedKnowledgeSnapshot(snapshot)
                .contextSnapshot(snapshot)
                .aiReferenceMappingSnapshot(AiReferenceMappingSnapshot.from(registry).asMap())
                .build();
    }
}
