package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.engineeringcontext.mapper.EngineeringContextContractMapper;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext.SelectionDecision;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineeringContextContractMapperTest {

    private static final String SLUG = "devlog-ai";
    private static final java.util.UUID PROJECT_ID =
            java.util.UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

    private final ProjectContextContractMapper projectContextContractMapper =
            mock(ProjectContextContractMapper.class);

    private final EngineeringContextContractMapper mapper =
            new EngineeringContextContractMapper(projectContextContractMapper);

    private ProjectContextSnapshot projectSnapshotWithSlug() {
        return new ProjectContextSnapshot(
                new com.hopeful117.devlogai.analysis.context.AnalysisContext.ProjectSnapshot(
                        PROJECT_ID, SLUG, SLUG, "description",
                        com.hopeful117.devlogai.project.entity.ProjectStatus.ACTIVE),
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    @Test
    void shouldMapEngineeringContextWithEvidenceAndSelectionReason() {
        ProjectContextSnapshot projectSnapshot = projectSnapshotWithSlug();

        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class
        );

        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        RepositoryEvidence evidence = mock(RepositoryEvidence.class);
        RepositoryEvidence.EvidenceProvenance provenance =
                mock(RepositoryEvidence.EvidenceProvenance.class);

        when(evidence.reference()).thenReturn("evidence-1");
        when(evidence.kind()).thenReturn("CHANGED_FILE");
        when(evidence.layer()).thenReturn(
                com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer.COMMIT_DIFF
        );
        when(evidence.summary()).thenReturn("Project note rendering changed");
        when(evidence.provenance()).thenReturn(provenance);
        when(evidence.relevanceScore()).thenReturn(92);

        when(provenance.sourceType()).thenReturn("GIT");
        when(provenance.originatingFile())
                .thenReturn("frontend/project-context-inputs-section.html");
        when(provenance.identifier()).thenReturn("abc123");

        RepositoryContext.SelectionDecision selectionDecision =
                new RepositoryContext.SelectionDecision(
                        "evidence-1",
                        true,
                        "SELECTED_BY_RANK",
                        92,
                        120
                );

        RepositoryContext repositoryContext = mock(RepositoryContext.class);

        when(repositoryContext.evidence()).thenReturn(List.of(evidence));
        when(repositoryContext.selectionDecisions())
                .thenReturn(List.of(selectionDecision));
        when(repositoryContext.candidateCount()).thenReturn(4);
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(120);
        when(repositoryContext.contextDigest()).thenReturn("digest-123");
        when(repositoryContext.warnings()).thenReturn(List.of());

        String intent =
                "Investigate why Project Notes Markdown is displayed incorrectly.";

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, intent, List.of(), null, null);

        assertThat(result.project()).isSameAs(mappedProject);
        assertThat(result.intent()).isEqualTo(intent);

        assertThat(result.evidence()).hasSize(1);

        var mappedEvidence = result.evidence().getFirst();

        assertThat(mappedEvidence.kind()).isEqualTo("CHANGED_FILE");
        assertThat(mappedEvidence.layer()).isEqualTo("COMMIT_DIFF");
        assertThat(mappedEvidence.summary())
                .isEqualTo("Project note rendering changed");

        assertThat(mappedEvidence.sourceType()).isEqualTo("GIT");
        assertThat(mappedEvidence.originatingFile())
                .isEqualTo("frontend/project-context-inputs-section.html");
        assertThat(mappedEvidence.identifier()).isEqualTo("abc123");

        assertThat(mappedEvidence.relevanceScore()).isEqualTo(92);
        assertThat(mappedEvidence.selectionReason())
                .isEqualTo("SELECTED_BY_RANK");

        assertThat(result.metadata().candidateCount()).isEqualTo(4);
        assertThat(result.metadata().selectedCount()).isEqualTo(1);
        assertThat(result.metadata().truncated()).isFalse();
        assertThat(result.metadata().usedTokens()).isEqualTo(120);
        assertThat(result.metadata().contextDigest())
                .isEqualTo("digest-123");
    }

    @Test
    void shouldMapEnrichmentTemporalAndProvenanceInformation() {
        ProjectContextSnapshot projectSnapshot = projectSnapshotWithSlug();
        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class
        );
        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        var occurredAt = Instant.parse("2026-08-01T10:15:30Z");
        var content = new RepositoryEvidenceContent(
                RepositoryEvidenceContent.Status.TRUNCATED,
                "class Example {\n",
                "CONTENT_ENRICHMENT_TRUNCATED",
                "repository-content-policy", "v1",
                "9e1c2f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e");
        var symbols = new RepositoryEvidenceSymbols(
                RepositoryEvidenceSymbols.Status.EXTRACTED,
                null,
                "repository-symbol-policy", "v1",
                "java-declaration-extractor", "v1",
                "9e1c2f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e",
                1, List.of("RANK_1"), false, 1, 1,
                List.of(new RepositoryEvidenceSymbols.JavaDeclaration(
                        RepositoryEvidenceSymbols.Kind.METHOD,
                        "getEngineeringContext",
                        "EngineeringContextController",
                        List.of("public"),
                        "ResponseEntity<EngineeringContext>",
                        List.of(new RepositoryEvidenceSymbols.Parameter(
                                "String", "projectSlug")),
                        List.of("@GetMapping"),
                        new RepositoryEvidenceSymbols.SourceLocation(15, 4, 19, 5))));
        RepositoryEvidence evidence = new RepositoryEvidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "SOURCE_FILE",
                "file:backend/src/main/java/EngineeringContextController.java",
                "backend/src/main/java/EngineeringContextController.java",
                occurredAt,
                EvidenceScore.unscored(),
                List.of("diff:abc123:backend/src/main/java/EngineeringContextController.java"),
                new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY_STRUCTURE",
                        "source-id-1",
                        "backend/src/main/java/EngineeringContextController.java",
                        "repository-structure:source-file:backend/src/main/java/EngineeringContextController.java"),
                java.util.Map.of("resolvedRevision",
                        "9e1c2f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e"),
                210,
                List.of("COLLECTED_NOT_RANKED"),
                content,
                symbols);

        SelectionDecision selectionDecision = new SelectionDecision(
                evidence.reference(), true, "SELECTED_BY_RANK", 88, 210);

        RepositoryContext repositoryContext = mock(RepositoryContext.class);
        when(repositoryContext.evidence()).thenReturn(List.of(evidence));
        when(repositoryContext.selectionDecisions())
                .thenReturn(List.of(selectionDecision));
        when(repositoryContext.candidateCount()).thenReturn(9);
        when(repositoryContext.truncated()).thenReturn(true);
        when(repositoryContext.usedTokens()).thenReturn(4800);
        when(repositoryContext.contextDigest()).thenReturn("digest-enriched");
        when(repositoryContext.warnings()).thenReturn(List.of(
                "REPOSITORY_CONTEXT_BUDGET_APPLIED",
                "CONTENT_ENRICHMENT_TRUNCATED"));

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, "intent", List.of(), null, null);

        assertThat(result.evidence()).hasSize(1);
        var mapped = result.evidence().getFirst();

        assertThat(mapped.occurredAt()).isEqualTo(occurredAt);
        assertThat(mapped.relatedReferences()).containsExactly(
                "diff:abc123:backend/src/main/java/EngineeringContextController.java");
        assertThat(mapped.extractionMetadata())
                .containsEntry("resolvedRevision",
                        "9e1c2f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e");

        assertThat(mapped.content()).isNotNull();
        assertThat(mapped.content().status()).isEqualTo("TRUNCATED");
        assertThat(mapped.content().text()).isEqualTo("class Example {\n");
        assertThat(mapped.content().reason()).isEqualTo("CONTENT_ENRICHMENT_TRUNCATED");
        assertThat(mapped.content().revision())
                .isEqualTo("9e1c2f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e");

        assertThat(mapped.symbols()).isNotNull();
        assertThat(mapped.symbols().status()).isEqualTo("EXTRACTED");
        assertThat(mapped.symbols().truncated()).isFalse();
        assertThat(mapped.symbols().returnedSymbolCount()).isEqualTo(1);
        assertThat(mapped.symbols().extractorId()).isEqualTo("java-declaration-extractor");
        assertThat(mapped.symbols().revision())
                .isEqualTo("9e1c2f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e");
        assertThat(mapped.symbols().declarations()).hasSize(1);
        var declaration = mapped.symbols().declarations().getFirst();
        assertThat(declaration.kind()).isEqualTo("METHOD");
        assertThat(declaration.name()).isEqualTo("getEngineeringContext");
        assertThat(declaration.owningType()).isEqualTo("EngineeringContextController");
        assertThat(declaration.modifiers()).containsExactly("public");
        assertThat(declaration.returnType()).isEqualTo("ResponseEntity<EngineeringContext>");
        assertThat(declaration.parameters()).hasSize(1);
        assertThat(declaration.parameters().getFirst().type()).isEqualTo("String");
        assertThat(declaration.parameters().getFirst().name()).isEqualTo("projectSlug");
        assertThat(declaration.annotations()).containsExactly("@GetMapping");
        assertThat(declaration.location().beginLine()).isEqualTo(15);

        assertThat(result.metadata().warnings()).containsExactly(
                "REPOSITORY_CONTEXT_BUDGET_APPLIED",
                "CONTENT_ENRICHMENT_TRUNCATED",
                "PROJECT_CONTEXT_STALE");
        assertThat(result.metadata().truncated()).isTrue();
    }

    @Test
    void shouldMapMissingInformationAsCleanAbsence() {
        ProjectContextSnapshot projectSnapshot = projectSnapshotWithSlug();
        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class
        );
        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        RepositoryEvidence evidence = new RepositoryEvidence(
                RepositoryContextLayer.GIT_HISTORY,
                "COMMIT",
                "git:source-1:abc123",
                "Fix markdown rendering — 2 files, +40/-12",
                Instant.parse("2026-07-15T08:00:00Z"),
                EvidenceScore.unscored(),
                List.of("git:source-1:parent1"),
                new RepositoryEvidence.EvidenceProvenance(
                        "GIT", "source-1", null, null),
                java.util.Map.of("collectorId", "git-history",
                        "collectorVersion", "v1"),
                60,
                List.of("COLLECTED_NOT_RANKED"));

        RepositoryContext repositoryContext = mock(RepositoryContext.class);
        when(repositoryContext.evidence()).thenReturn(List.of(evidence));
        when(repositoryContext.selectionDecisions()).thenReturn(java.util.List.of());
        when(repositoryContext.candidateCount()).thenReturn(1);
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(60);
        when(repositoryContext.contextDigest()).thenReturn("digest-minimal");
        when(repositoryContext.warnings()).thenReturn(java.util.List.of());

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, "intent", List.of(), null, null);

        var mapped = result.evidence().getFirst();
        assertThat(mapped.occurredAt())
                .isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
        assertThat(mapped.relatedReferences()).containsExactly("git:source-1:parent1");
        assertThat(mapped.content()).isNull();
        assertThat(mapped.symbols()).isNull();
        assertThat(mapped.selectionReason()).isNull();
        assertThat(mapped.resource()).isNull();
        assertThat(result.metadata().warnings()).isEmpty();
    }

    @Test
    void shouldAttachResourceUriToExactlyAddressableEvidence() {
        ProjectContextSnapshot projectSnapshot = projectSnapshotWithSlug();
        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class);
        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        var decisionId = java.util.UUID.randomUUID();
        var insightId = java.util.UUID.randomUUID();
        var storyId = java.util.UUID.randomUUID();
        var eventId = java.util.UUID.randomUUID();
        String sha = "3cd3723206eae38d518eb696a1dd50c0476264d0";

        RepositoryContext repositoryContext = mock(RepositoryContext.class);
        when(repositoryContext.evidence()).thenReturn(List.of(
                evidence("DECISION", decisionId.toString(), null),
                evidence("INSIGHT", insightId.toString(), null),
                evidence("ENGINEERING_STORY", storyId.toString(), null),
                evidence("ENGINEERING_EVENT", eventId.toString(), null),
                new RepositoryEvidence(
                        RepositoryContextLayer.GIT_HISTORY, "COMMIT",
                        "git:" + PROJECT_ID + ":" + sha,
                        "Fix markdown rendering", Instant.now(),
                        EvidenceScore.unscored(), List.of(),
                        new RepositoryEvidence.EvidenceProvenance(
                                "GIT", PROJECT_ID.toString(), null, null),
                        java.util.Map.of(), 60, List.of())));
        when(repositoryContext.selectionDecisions()).thenReturn(java.util.List.of());
        when(repositoryContext.candidateCount()).thenReturn(5);
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(300);
        when(repositoryContext.contextDigest()).thenReturn("digest");
        when(repositoryContext.warnings()).thenReturn(java.util.List.of());

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, "intent", List.of(), null, null);

        assertThat(result.evidence()).hasSize(5);

        // Verify all expected resources are present (order determined by trust tier + occurredAt)
        List<String> resources = result.evidence().stream()
                .map(EngineeringEvidence::resource)
                .toList();
        assertThat(resources).contains(
                "devlog://projects/devlog-ai/decisions/" + decisionId,
                "devlog://projects/devlog-ai/insights/" + insightId,
                "devlog://projects/devlog-ai/stories/" + storyId,
                "devlog://projects/devlog-ai/engineering-events/" + eventId,
                "devlog://projects/devlog-ai/commits/" + sha
        );
    }

    @Test
    void shouldLeaveNonAddressableEvidenceWithoutResource() {
        ProjectContextSnapshot projectSnapshot = projectSnapshotWithSlug();
        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class);
        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        RepositoryContext repositoryContext = mock(RepositoryContext.class);
        when(repositoryContext.evidence()).thenReturn(List.of(
                evidence("CHANGED_FILE", "commit-diff:src/App.java", null),
                evidence("CHALLENGE", java.util.UUID.randomUUID().toString(), null),
                evidence("MILESTONE", java.util.UUID.randomUUID().toString(), null),
                evidence("ANALYSIS", java.util.UUID.randomUUID().toString(), null),
                evidence("ARTIFACT", java.util.UUID.randomUUID().toString(), null),
                evidence("SOURCE_FILE", "repository-structure:source-file:src/App.java",
                        "src/App.java")));
        when(repositoryContext.selectionDecisions()).thenReturn(java.util.List.of());
        when(repositoryContext.candidateCount()).thenReturn(6);
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(360);
        when(repositoryContext.contextDigest()).thenReturn("digest");
        when(repositoryContext.warnings()).thenReturn(java.util.List.of());

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, "intent", List.of(), null, null);

        assertThat(result.evidence())
                .allSatisfy(e -> assertThat(e.resource()).isNull());
    }

    @Test
    void shouldSafelyIgnoreInvalidOrUnexpectedIdentifiers() {
        ProjectContextSnapshot projectSnapshot = projectSnapshotWithSlug();
        var mappedProject = mock(
                com.hopeful117.devlogai.contracts.projectcontext.ProjectContext.class);
        when(projectContextContractMapper.toContract(projectSnapshot))
                .thenReturn(mappedProject);

        RepositoryContext repositoryContext = mock(RepositoryContext.class);
        when(repositoryContext.evidence()).thenReturn(List.of(
                evidence("DECISION", "not-a-uuid", null),
                evidence("INSIGHT", null, null),
                new RepositoryEvidence(
                        RepositoryContextLayer.GIT_HISTORY, "COMMIT",
                        "git:not-a-source:shortsha",
                        "Weird commit", Instant.now(),
                        EvidenceScore.unscored(), List.of(),
                        new RepositoryEvidence.EvidenceProvenance(
                                "GIT", "not-a-source", null, null),
                        java.util.Map.of(), 60, List.of()),
                evidence("UNKNOWN_KIND", java.util.UUID.randomUUID().toString(), null)));
        when(repositoryContext.selectionDecisions()).thenReturn(java.util.List.of());
        when(repositoryContext.candidateCount()).thenReturn(4);
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(240);
        when(repositoryContext.contextDigest()).thenReturn("digest");
        when(repositoryContext.warnings()).thenReturn(java.util.List.of());

        EngineeringContext result =
                mapper.toContract(projectSnapshot, repositoryContext, "intent", List.of(), null, null);

        // UNKNOWN_KIND with CORE_KNOWLEDGE sourceType is excluded by the trust tier classifier
        // (unsupported kinds are excluded, not silently classified)
        assertThat(result.evidence()).hasSize(3);
        assertThat(result.evidence())
                .allSatisfy(e -> assertThat(e.resource()).isNull());
    }

    private RepositoryEvidence evidence(
            String kind, String identifier, String originatingFile) {
        String sourceType = switch (kind) {
            case "DECISION", "INSIGHT", "ENGINEERING_EVENT" -> "CORE_KNOWLEDGE";
            case "CHANGED_FILE", "SOURCE_FILE", "COMMIT" -> "GIT";
            default -> "CORE_KNOWLEDGE";
        };
        return new RepositoryEvidence(
                switch (kind) {
                    case "DECISION" -> RepositoryContextLayer.ADR;
                    case "INSIGHT" -> RepositoryContextLayer.VALIDATED_INSIGHT;
                    case "ENGINEERING_EVENT" -> RepositoryContextLayer.GIT_HISTORY;
                    case "MILESTONE", "CHALLENGE", "ENGINEERING_STORY" ->
                            RepositoryContextLayer.ROADMAP;
                    case "ARTIFACT" -> RepositoryContextLayer.PROJECT_DOCUMENTATION;
                    case "ANALYSIS" -> RepositoryContextLayer.PREVIOUS_ANALYSIS;
                    default -> RepositoryContextLayer.RELATED_SOURCE_CODE;
                },
                kind,
                kind.toLowerCase() + ":ref",
                "summary of " + kind,
                Instant.parse("2026-08-01T10:00:00Z"),
                EvidenceScore.unscored(),
                List.of(),
                new RepositoryEvidence.EvidenceProvenance(
                        sourceType, null, originatingFile, identifier),
                java.util.Map.of(),
                60,
                List.of());
    }


    // ---- freshness metadata (story 0091 / ADR-062) ----

    private com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary summary(
            com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse... rows) {
        return new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary(
                "project-freshness-summary-v1", PROJECT_ID, java.util.Arrays.asList(rows),
                0, false);
    }

    private com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse row(
            java.util.UUID sourceId, String name, String observed,
            String baselineRevision,
            com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus status) {
        var source = new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse.Source(
                sourceId, name, "main", null, observed, null);
        var baseline = baselineRevision == null ? null
                : new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse.Baseline(
                        java.util.UUID.randomUUID(), Instant.now(), baselineRevision);
        return new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse(
                "project-freshness-v1", java.util.UUID.randomUUID(), PROJECT_ID, source,
                Instant.parse("2026-08-26T10:00:00Z"), status,
                com.hopeful117.devlogai.projectfreshness.ProjectRefreshGuidance.REFRESH_NOT_NEEDED,
                baseline,
                new com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse.ReviewCounts(
                        0, 0, 0, 0));
    }

    private RepositoryContext contextWithRevisions(java.util.List<String> revisions) {
        RepositoryContext repositoryContext = mock(RepositoryContext.class);
        java.util.List<RepositoryEvidence> evidence = revisions.stream()
                .map(revision -> new RepositoryEvidence(
                        RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                        "repository-structure:source-file:src/App.java",
                        "App.java", Instant.now(), EvidenceScore.unscored(), List.of(),
                        new RepositoryEvidence.EvidenceProvenance(
                                "REPOSITORY_STRUCTURE", "source-1", "src/App.java",
                                "repository-structure:source-file:src/App.java"),
                        java.util.Map.of("resolvedRevision", revision),
                        60, List.of()))
                .toList();
        when(repositoryContext.evidence()).thenReturn(evidence);
        when(repositoryContext.selectionDecisions()).thenReturn(List.of());
        when(repositoryContext.candidateCount()).thenReturn(evidence.size());
        when(repositoryContext.truncated()).thenReturn(false);
        when(repositoryContext.usedTokens()).thenReturn(60);
        when(repositoryContext.contextDigest()).thenReturn("digest");
        when(repositoryContext.warnings()).thenReturn(List.of());
        return repositoryContext;
    }

    @Test
    void shouldDeclareFreshSingleSourceContext() {
        var sourceId = java.util.UUID.randomUUID();
        String revision = "a".repeat(40);
        var repositoryContext = contextWithRevisions(List.of(revision));
        var freshnessSummary = summary(row(sourceId, "devlog-ai", revision, revision,
                com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus.CURRENT));

        var result = mapper.toContract(projectSnapshotWithSlug(), repositoryContext,
                "intent", List.of(), null, freshnessSummary);

        var freshness = result.metadata().freshness();
        assertThat(freshness).isNotNull();
        assertThat(freshness.status())
                .isEqualTo(com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness.STATUS_CURRENT);
        assertThat(freshness.repositoryRevision()).isEqualTo(revision);
        assertThat(freshness.contextRevision()).isEqualTo(revision);
        assertThat(freshness.sources()).hasSize(1);
        assertThat(result.metadata().warnings())
                .doesNotContain("PROJECT_CONTEXT_STALE", "PROJECT_CONTEXT_PARTIALLY_FRESH");
    }

    @Test
    void shouldWarnWhenServedRevisionDivergesFromKnowledgeBaseline() {
        var sourceId = java.util.UUID.randomUUID();
        String baseline = "b".repeat(40);
        String liveHead = "c".repeat(40);
        var repositoryContext = contextWithRevisions(List.of(liveHead));
        var freshnessSummary = summary(row(sourceId, "devlog-ai", baseline, baseline,
                com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus.CURRENT));

        var result = mapper.toContract(projectSnapshotWithSlug(), repositoryContext,
                "intent", List.of(), null, freshnessSummary);

        var freshness = result.metadata().freshness();
        assertThat(freshness.status())
                .isEqualTo(com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness.STATUS_STALE);
        assertThat(freshness.repositoryRevision()).isEqualTo(liveHead);
        assertThat(freshness.contextRevision()).isEqualTo(baseline);
        assertThat(freshness.sources().getFirst().status())
                .isEqualTo(com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness.STATUS_STALE);
        assertThat(freshness.sources().getFirst().observedRevision()).isEqualTo(liveHead);
        assertThat(result.metadata().warnings()).contains("PROJECT_CONTEXT_STALE");
    }

    @Test
    void shouldReportPartiallyFreshWhenSourcesDisagree() {
        String liveHead = "d".repeat(40);
        var repositoryContext = contextWithRevisions(List.of());
        var freshnessSummary = summary(
                row(java.util.UUID.randomUUID(), "current-repo", liveHead, liveHead,
                        com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus.CURRENT),
                row(java.util.UUID.randomUUID(), "stale-repo", "e".repeat(40), "f".repeat(40),
                        com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus.STALE));

        var result = mapper.toContract(projectSnapshotWithSlug(), repositoryContext,
                "intent", List.of(), null, freshnessSummary);

        var freshness = result.metadata().freshness();
        assertThat(freshness.status())
                .isEqualTo(com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness.STATUS_PARTIALLY_FRESH);
        assertThat(freshness.repositoryRevision()).isNull();
        assertThat(freshness.contextRevision()).isNull();
        assertThat(freshness.sources()).hasSize(2);
        assertThat(result.metadata().warnings()).contains(
                "PROJECT_CONTEXT_STALE", "PROJECT_CONTEXT_PARTIALLY_FRESH");
    }

    @Test
    void shouldReportNoBaselineInsteadOfCleanStateWhenOnlyObservationExists() {
        String liveHead = "1".repeat(40);
        var repositoryContext = contextWithRevisions(List.of(liveHead));

        var result = mapper.toContract(projectSnapshotWithSlug(), repositoryContext,
                "intent", List.of(), null, null);

        var freshness = result.metadata().freshness();
        assertThat(freshness.status())
                .isEqualTo(com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness.STATUS_NO_BASELINE);
        assertThat(freshness.repositoryRevision()).isEqualTo(liveHead);
        assertThat(freshness.contextRevision()).isNull();
        assertThat(result.metadata().warnings()).contains("PROJECT_CONTEXT_STALE");
    }
}
