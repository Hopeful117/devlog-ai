package com.hopeful117.devlogai.analysis.evidence.projection;

import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.Availability;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProjectedSnapshot;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryContentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalSelectedEvidenceSnapshotProjectorTest {
    private static final UUID TASK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ANALYSIS_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROJECT_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String VERSION = "knowledge-selection-v4";
    private static final String DIGEST = "a".repeat(64);

    private final HistoricalSelectedEvidenceSnapshotProjector projector =
            new HistoricalSelectedEvidenceSnapshotProjector(new ObjectMapper());

    @Test
    void shouldProjectAllApprovedCategoriesAndMetadata() {
        ProjectedSnapshot result = project(completeSnapshot("COMPLETE"));

        assertEquals(PROJECT_ID, result.metadata().project().id());
        assertEquals(ANALYSIS_ID, result.metadata().analysis().id());
        assertEquals("profile summary", result.metadata().projectProfile().deterministicSummary());
        assertEquals(2, result.metadata().projectProfile().characteristicCount());
        assertTrue(result.metadata().diagnostics().collectionComplete());
        assertEquals(VERSION, result.metadata().selection().selectionVersion());
        assertEquals(1, result.metadata().selection().selectedKnowledgeCount());
        assertEquals("repository-context-engine-v1",
                result.metadata().repositoryContext().contextVersion());

        assertEquals(1, result.categories().facts().count());
        assertEquals("fact content", result.categories().facts().items().getFirst().content());
        assertEquals(1, result.categories().observations().count());
        assertEquals(1, result.categories().priorInsights().count());
        assertEquals(1, result.categories().architectureKnowledge().count());
        assertEquals(1, result.categories().engineeringEvents().count());
        assertEquals(1, result.categories().humanContext().count());
        assertEquals("# Human text",
                result.categories().humanContext().items().getFirst().contentMarkdown());
        assertEquals(1, result.categories().evolutionContext().count());
        assertEquals("src/App.java", result.categories().evolutionContext().items().getFirst()
                .commitDiff().changedFiles().getFirst().newPath());
        assertEquals(1, result.categories().repositoryEvidence().count());
        assertEquals(RepositoryContentStatus.COMPLETE,
                result.categories().repositoryEvidence().items().getFirst().content().status());
        assertEquals("App", result.categories().repositoryEvidence().items().getFirst()
                .symbols().declarations().getFirst().name());
        assertEquals(1, result.categories().repositoryEvidence().items().getFirst()
                .symbols().declarations().getFirst().location().beginLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "knowledge-selection-v1",
            "knowledge-selection-v2",
            "knowledge-selection-v3",
            "knowledge-selection-v4"
    })
    void shouldSupportObservedVersionsWithPresenceAwareCategories(String version) {
        Map<String, Object> snapshot = minimalSnapshot(version);

        ProjectedSnapshot result = projector.project(
                TASK_ID, version, DIGEST, ANALYSIS_ID, PROJECT_ID, snapshot);

        assertEquals(Availability.RECORDED, result.categories().facts().availability());
        assertEquals(Availability.NOT_RECORDED,
                result.categories().humanContext().availability());
        assertEquals(Availability.NOT_RECORDED,
                result.categories().repositoryEvidence().availability());
    }

    @Test
    void shouldDistinguishMissingRecordedEmptyAndGloballyEmpty() {
        Map<String, Object> snapshot = minimalSnapshot(VERSION);
        snapshot.put("selectedObservations", List.of());
        snapshot.put("selectedInsights", List.of());
        snapshot.put("existingArchitectureKnowledge", List.of());
        snapshot.put("selectedEngineeringEvents", List.of());
        snapshot.put("selectedHumanContextInputs", List.of());
        snapshot.put("repositoryContext", null);
        snapshot.put("evolutionContext", null);

        ProjectedSnapshot result = project(snapshot);

        assertEquals(Availability.RECORDED, result.categories().facts().availability());
        assertEquals(0, result.categories().facts().count());
        assertEquals(Availability.RECORDED, result.categories().repositoryEvidence().availability());
        assertEquals(0, result.categories().repositoryEvidence().count());
        assertEquals(Availability.RECORDED, result.categories().evolutionContext().availability());
        assertEquals(0, result.categories().evolutionContext().count());
        assertTrue(List.of(
                result.categories().facts().count(),
                result.categories().observations().count(),
                result.categories().priorInsights().count(),
                result.categories().architectureKnowledge().count(),
                result.categories().engineeringEvents().count(),
                result.categories().humanContext().count(),
                result.categories().evolutionContext().count(),
                result.categories().repositoryEvidence().count()
        ).stream().allMatch(count -> count == 0));
    }

    @Test
    void shouldIgnoreUnknownExtraKeys() {
        Map<String, Object> snapshot = minimalSnapshot(VERSION);
        snapshot.put("futureCategory", map("sensitive", "ignored"));

        ProjectedSnapshot result = project(snapshot);

        assertEquals(0, result.categories().facts().count());
    }

    @Test
    void shouldRejectMalformedKnownCategoryAndFieldTypes() {
        Map<String, Object> malformedCategory = minimalSnapshot(VERSION);
        malformedCategory.put("selectedFacts", "not-a-list");
        assertFailureAt(malformedCategory, "selectedFacts");

        Map<String, Object> malformedField = completeSnapshot("COMPLETE");
        firstMap(malformedField, "selectedFacts").put("content", 42);
        assertFailureAt(malformedField, "selectedFacts[0].content");

        Map<String, Object> malformedObject = minimalSnapshot(VERSION);
        malformedObject.put("repositoryContext", List.of());
        assertFailureAt(malformedObject, "repositoryContext");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "knowledge-selection-v5"})
    void shouldRejectMissingBlankAndUnknownVersions(String version) {
        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException exception =
                assertThrows(
                        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException.class,
                        () -> projector.project(TASK_ID, version, DIGEST,
                                ANALYSIS_ID, PROJECT_ID, minimalSnapshot(VERSION)));

        assertTrue(exception.getMessage().contains("path=selectionVersion"));
        assertNull(exception.getCause());
    }

    @Test
    void shouldRejectAnalysisAndProjectIdentityMismatches() {
        Map<String, Object> wrongAnalysis = minimalSnapshot(VERSION);
        wrongAnalysis.put("analysis", map("id", UUID.randomUUID().toString()));
        assertFailureAt(wrongAnalysis, "analysis.id");

        Map<String, Object> wrongProject = minimalSnapshot(VERSION);
        wrongProject.put("project", map("id", UUID.randomUUID().toString()));
        assertFailureAt(wrongProject, "project.id");
    }

    @Test
    void shouldRejectEmbeddedSelectionVersionAndDigestMismatches() {
        Map<String, Object> wrongVersion = minimalSnapshot(VERSION);
        wrongVersion.put("selectionMetadata", map("selectionVersion", "knowledge-selection-v3"));
        assertFailureAt(wrongVersion, "selectionMetadata.selectionVersion");

        Map<String, Object> wrongDigest = minimalSnapshot(VERSION);
        wrongDigest.put("selectionDigest", "b".repeat(64));
        assertFailureAt(wrongDigest, "selectionDigest");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETE", "TRUNCATED", "SKIPPED", "UNAVAILABLE"})
    void shouldPreserveRepositoryContentStatuses(String status) {
        ProjectedSnapshot result = project(completeSnapshot(status));

        assertEquals(RepositoryContentStatus.valueOf(status),
                result.categories().repositoryEvidence().items().getFirst().content().status());
    }

    @Test
    void shouldRejectUnknownRepositoryContentStatus() {
        assertFailureAt(completeSnapshot("PARTIAL"),
                "repositoryContext.evidence[0].content.status");
    }

    @Test
    void shouldKeepFailureMessagesAndCausesFreeOfEvidenceBodies() {
        String sensitiveBody = "private-source-body-should-not-be-logged";
        Map<String, Object> snapshot = minimalSnapshot(VERSION);
        snapshot.put("selectedHumanContextInputs", List.of(map(
                "contentMarkdown", sensitiveBody,
                "updatedAt", 99
        )));

        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException exception =
                assertThrows(
                        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException.class,
                        () -> project(snapshot));

        assertTrue(exception.getMessage().contains("task=" + TASK_ID));
        assertTrue(exception.getMessage().contains("version=" + VERSION));
        assertTrue(exception.getMessage().contains("path=selectedHumanContextInputs[0].updatedAt"));
        assertFalse(exception.getMessage().contains(sensitiveBody));
        assertNull(exception.getCause());
    }

    private ProjectedSnapshot project(Map<String, Object> snapshot) {
        return projector.project(TASK_ID, VERSION, DIGEST, ANALYSIS_ID, PROJECT_ID, snapshot);
    }

    private void assertFailureAt(Map<String, Object> snapshot, String path) {
        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException exception =
                assertThrows(
                        HistoricalSelectedEvidenceSnapshotProjector.HistoricalSnapshotReadException.class,
                        () -> project(snapshot));
        assertTrue(exception.getMessage().contains("path=" + path));
        assertNull(exception.getCause());
    }

    private Map<String, Object> minimalSnapshot(String version) {
        return map(
                "project", map("id", PROJECT_ID.toString()),
                "analysis", map("id", ANALYSIS_ID.toString()),
                "selectedFacts", List.of(),
                "selectionMetadata", map("selectionVersion", version),
                "selectionDigest", DIGEST
        );
    }

    private Map<String, Object> completeSnapshot(String contentStatus) {
        UUID factId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID observationId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        UUID sourceId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID proposalId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID insightId = UUID.fromString("80000000-0000-0000-0000-000000000001");

        return map(
                "project", map(
                        "id", PROJECT_ID.toString(), "name", "Project", "slug", "project",
                        "description", "Description", "status", "ACTIVE"),
                "analysis", map(
                        "id", ANALYSIS_ID.toString(), "type", "ARCHITECTURE_REVIEW",
                        "intentId", "architecture-overview", "intentVersion", "v1",
                        "status", "COMPLETED", "startedAt", "2026-08-27T10:00:00Z",
                        "completedAt", "2026-08-27T10:01:00Z",
                        "createdAt", "2026-08-27T09:59:00Z"),
                "projectProfile", map(
                        "id", UUID.randomUUID().toString(), "projectId", PROJECT_ID.toString(),
                        "analysisId", ANALYSIS_ID.toString(), "profileVersion", "v1",
                        "rendererVersion", "r1", "generatedAt", "2026-08-27T10:00:00Z",
                        "requestedRevision", "abc123", "completeness", map(
                                "status", "COMPLETE", "collectionComplete", true,
                                "truncated", false, "warningCount", 0, "errorCount", 0,
                                "successfulCollectorCount", 2,
                                "collectorsWithWarningsCount", 0, "failedCollectorCount", 0),
                        "sections", List.of(map("private", "excluded")),
                        "resolvedRevisions", map("private", "excluded"),
                        "sourceObservations", List.of(map("private", "excluded")),
                        "deterministicSummary", "profile summary", "characteristicCount", 2),
                "selectedFacts", List.of(map(
                        "id", factId.toString(), "type", "SOURCE_DIRECTORY_PRESENT",
                        "content", "fact content", "source", "src/App.java",
                        "evidenceReferences", List.of("fact:1"),
                        "detectedAt", "2026-08-27T10:00:00Z")),
                "selectedObservations", List.of(map(
                        "id", observationId.toString(), "type", "ARCHITECTURAL_PATTERN",
                        "content", "observation content", "ruleId", "rule-1",
                        "ruleVersion", "v1", "supportingFactIds", List.of(factId.toString()),
                        "createdAt", "2026-08-27T10:00:01Z")),
                "diagnostics", map(
                        "collectionComplete", true, "truncated", false,
                        "warningCount", 0, "errorCount", 0),
                "selectedInsights", List.of(map(
                        "type", "ARCHITECTURAL", "severity", "INFO",
                        "title", "Prior insight", "content", "prior content")),
                "existingArchitectureKnowledge", List.of(map(
                        "insightId", insightId.toString(), "proposalId", proposalId.toString(),
                        "normalizedType", "ARCHITECTURAL", "severity", "INFO",
                        "sourceType", "ARCHITECTURAL", "title", "Architecture",
                        "content", "architecture content", "rationale", "because",
                        "evidenceReferences", List.of("git:1"),
                        "createdAt", "2026-08-27T10:00:02Z")),
                "selectedEngineeringEvents", List.of(map(
                        "id", UUID.randomUUID().toString(), "category", "ARCHITECTURE",
                        "title", "Event", "summary", "event summary",
                        "sourceId", sourceId.toString(), "baseCommit", "base",
                        "targetCommit", "target", "occurredAt", "2026-08-27T10:00:03Z",
                        "proposalId", proposalId.toString())),
                "selectedHumanContextInputs", List.of(map(
                        "id", UUID.randomUUID().toString(), "type", "GOAL",
                        "title", "Human goal", "contentMarkdown", "# Human text",
                        "status", "ACTIVE", "updatedAt", "2026-08-27T10:00:04Z")),
                "repositoryContext", repositoryContext(contentStatus),
                "evolutionContext", map(
                        "contextVersion", "evolution-v1", "projectId", PROJECT_ID.toString(),
                        "sourceId", sourceId.toString(), "baseCommit", "base",
                        "targetCommit", "target", "comparisonPolicy", "FIRST_PARENT",
                        "mergeCommit", false, "targetCommittedAt", "2026-08-27T10:00:05Z",
                        "commitDiff", map(
                                "projectId", PROJECT_ID.toString(),
                                "repositoryId", sourceId.toString(), "commitHash", "target",
                                "firstParentHash", "base", "parentHashes", List.of("base"),
                                "rootCommit", false, "mergeCommit", false,
                                "commitMessage", "Change App", "committedAt", "2026-08-27T10:00:05Z",
                                "changedFiles", List.of(map(
                                        "changeType", "MODIFIED", "oldPath", "src/App.java",
                                        "newPath", "src/App.java", "binary", false,
                                        "insertions", 2, "deletions", 1, "language", "Java",
                                        "category", "SOURCE", "excludedFromAnalysis", false,
                                        "exclusionReason", null, "evidenceReference", "diff:1")),
                                "statistics", map("filesChanged", 1, "insertions", 2,
                                        "deletions", 1, "binaryFiles", 0),
                                "candidateAdrReferences", List.of("ADR-063"),
                                "candidateRoadmapReferences", List.of(),
                                "evidenceReferences", List.of("diff:1"),
                                "truncated", false, "warnings", List.of())),
                "selectionMetadata", map(
                        "selectionVersion", VERSION, "appliedRules", List.of("rule-v1"),
                        "selectedKnowledgeCount", 1, "discardedKnowledgeCount", 0,
                        "knowledgeBudget", map(
                                "maximumFacts", 40, "maximumObservations", 25,
                                "maximumInsights", 10, "maximumArchitectureKnowledge", 5,
                                "maximumRepositoryEvidence", 60),
                        "completeness", "COMPLETE"),
                "selectionDigest", DIGEST
        );
    }

    private Map<String, Object> repositoryContext(String contentStatus) {
        Map<String, Object> location = map(
                "beginLine", 1, "beginColumn", 1, "endLine", 2, "endColumn", 1);
        Map<String, Object> declaration = map(
                "kind", "CLASS", "name", "App", "owningType", null,
                "modifiers", List.of("public"), "returnType", null,
                "parameters", List.of(map("type", "String", "name", "value")),
                "annotations", List.of("Component"), "location", location);
        Map<String, Object> symbols = map(
                "status", "EXTRACTED", "reason", "symbols available",
                "policyId", "symbol-policy", "policyVersion", "v1",
                "extractorId", "javaparser", "extractorVersion", "3.27.0",
                "revision", "abc123", "allocationRank", 1,
                "truncated", false, "returnedSymbolCount", 1,
                "availableSymbolCount", 1, "declarations", List.of(declaration));
        Map<String, Object> content = map(
                "status", contentStatus, "text", "class App {}",
                "reason", "bounded content", "policyId", "content-policy",
                "policyVersion", "v1", "revision", "abc123",
                "allocationPolicyId", "allocation-policy",
                "allocationPolicyVersion", "v1", "allocationRank", 1);
        Map<String, Object> evidence = map(
                "layer", "CURRENT_ANALYSIS", "kind", "SOURCE_FILE",
                "reference", "src/App.java", "summary", "Application entry point",
                "occurredAt", "2026-08-27T10:00:00Z",
                "relatedReferences", List.of("pom.xml"),
                "content", content, "symbols", symbols);
        return map(
                "contextVersion", "repository-context-engine-v1",
                "profile", "PROJECT_STATE",
                "evidence", List.of(evidence),
                "warnings", List.of("bounded"),
                "contextDigest", "c".repeat(64)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMap(Map<String, Object> root, String key) {
        return (Map<String, Object>) ((List<?>) root.get(key)).getFirst();
    }

    private Map<String, Object> map(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}
