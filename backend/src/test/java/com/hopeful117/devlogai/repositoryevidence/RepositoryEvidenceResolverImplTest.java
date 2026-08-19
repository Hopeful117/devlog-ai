package com.hopeful117.devlogai.repositoryevidence;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryEvidenceResolverImplTest {

    @Mock
    private FactRepository factRepository;
    @Mock
    private ObservationRepository observationRepository;

    @InjectMocks
    private RepositoryEvidenceResolverImpl resolver;

    private UUID projectId;
    private UUID analysisId;
    private UUID sourceId;
    private Insight insight;
    private Analysis analysis;
    private ValidatableProposal proposal;
    private Project project;
    private Source source;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        analysisId = UUID.randomUUID();
        sourceId = UUID.randomUUID();

        project = new Project();
        project.setId(projectId);

        source = new Source();
        source.setId(sourceId);
        source.setActive(true);
        source.setType(SourceType.GIT_REPOSITORY);

        analysis = new Analysis();
        analysis.setId(analysisId);
        analysis.setProject(project);
        analysis.setSelectedSource(source);
        analysis.setTargetRevision("abc123");

        proposal = new ValidatableProposal();
        proposal.setId(UUID.randomUUID());
        proposal.setProject(project);
        proposal.setAnalysis(analysis);
        proposal.setType(ProposalType.INSIGHT);
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setSupportingFactIds(new java.util.ArrayList<>());
        proposal.setSupportingObservationIds(new java.util.ArrayList<>());
        proposal.setEvidenceReferences(new java.util.ArrayList<>());

        insight = new Insight();
        insight.setId(UUID.randomUUID());
        insight.setProject(project);
        insight.setAnalysis(analysis);
        insight.setProposal(proposal);
        insight.setType(InsightType.ARCHITECTURAL);
        insight.setStatus(InsightStatus.ACTIVE);
        insight.setTitle("Test");
        insight.setContent("Test");
        insight.setConfidence(BigDecimal.ONE);
        insight.setEvidenceReferences(new java.util.ArrayList<>());
    }

    private Fact fact(UUID id, String... refs) {
        Fact f = Fact.builder()
                .id(id)
                .analysis(analysis)
                .type(FactType.BUILD_MODULE_DECLARED)
                .content("content")
                .source("test")
                .fingerprint("fp-" + id)
                .detectedAt(Instant.now())
                .build();
        if (refs.length > 0) {
            Set<String> set = new LinkedHashSet<>();
            for (String r : refs) set.add(r);
            f.setEvidenceReferences(set);
        }
        return f;
    }

    private Observation observation(UUID id, Fact... facts) {
        Observation o = Observation.builder()
                .id(id)
                .analysis(analysis)
                .type(ObservationType.MULTI_MODULE_BUILD)
                .content("obs")
                .ruleId("rule")
                .ruleVersion("1")
                .createdAt(Instant.now())
                .build();
        Set<Fact> set = new LinkedHashSet<>();
        for (Fact f : facts) set.add(f);
        o.setSupportingFacts(set);
        return o;
    }

    // --- 1. resolves_DirectFacts_Union ---
    @Test
    void resolves_DirectFacts_Union() {
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1, f2));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1, f2)))
                .thenReturn(List.of(fact(f1, "src/main/Foo.java"), fact(f2, "src/main/Bar.java")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().resolvedFiles().size());
    }

    // --- 2. resolves_ObservationDerivedFacts ---
    @Test
    void resolves_ObservationDerivedFacts() {
        UUID obsId = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingObservationIds(List.of(obsId));

        when(observationRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(obsId)))
                .thenReturn(List.of(observation(obsId, fact(f1, "src/main/ObsFact.java"))));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().resolvedFiles().size());
        assertEquals("src/main/ObsFact.java", result.get().resolvedFiles().get(0).path());
    }

    // --- 3. deduplicates_ByFactId ---
    @Test
    void deduplicates_ByFactId() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));
        proposal.setSupportingObservationIds(List.of(UUID.randomUUID()));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1, "src/main/Dup.java")));
        when(observationRepository.findByAnalysisIdAndIdIn(any(), any()))
                .thenReturn(List.of(observation(UUID.randomUUID(), fact(f1, "src/main/Dup.java"))));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().resolvedFiles().size());
    }

    // --- 4. excludesNamespaceReferences ---
    @Test
    void excludesNamespaceReferences() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1,
                        "src/main/Valid.java",
                        "source:12345",
                        "repository:/path",
                        "git:abc123",
                        "analysis:foo",
                        "fact:bar",
                        "observation:baz",
                        "commit:123",
                        "diff:456",
                        "decision:789",
                        "insight:111",
                        "story:222",
                        "artifact:333",
                        "milestone:444")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().resolvedFiles().size());
        assertEquals("src/main/Valid.java", result.get().resolvedFiles().get(0).path());
    }

    // --- 5. excludesNonRelativePaths ---
    @Test
    void excludesNonRelativePaths() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1,
                        "src/main/Valid.java",
                        "/absolute/path",
                        "C:/windows/path",
                        "..",
                        "../parent",
                        "a/../b")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().resolvedFiles().size());
        assertEquals("src/main/Valid.java", result.get().resolvedFiles().get(0).path());
    }

    // --- 6. keepsPlainRelativePaths ---
    @Test
    void keepsPlainRelativePaths() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1,
                        "src/main/Valid.java",
                        "pom.xml",
                        "src/test/Test.java",
                        "deep/nested/path/File.java")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(4, result.get().resolvedFiles().size());
    }

    // --- 7. missingFactId_FailsClosed ---
    @Test
    void missingFactId_FailsClosed() {
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1, f2));

        // repo returns only f1
        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1, f2)))
                .thenReturn(List.of(fact(f1, "src/main/Foo.java")));

        assertThrows(RepositoryEvidenceResolutionException.class, () -> resolver.resolve(insight));
    }

    // --- 8. crossAnalysisFact_FailsClosed ---
    @Test
    void crossAnalysisFact_FailsClosed() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        Analysis otherAnalysis = new Analysis();
        otherAnalysis.setId(UUID.randomUUID());

        // repo returns fact with different analysis
        Fact crossFact = fact(f1, "src/main/Foo.java");
        crossFact.setAnalysis(otherAnalysis);

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(crossFact));

        assertThrows(RepositoryEvidenceResolutionException.class, () -> resolver.resolve(insight));
    }

    // --- 9. observationWithDanglingFact_FailsClosed ---
    @Test
    void observationWithDanglingFact_FailsClosed() {
        UUID obsId = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingObservationIds(List.of(obsId));

        Analysis otherAnalysis = new Analysis();
        otherAnalysis.setId(UUID.randomUUID());
        Fact dangling = fact(f1, "src/main/Foo.java");
        dangling.setAnalysis(otherAnalysis);

        when(observationRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(obsId)))
                .thenReturn(List.of(observation(obsId, dangling)));

        assertThrows(RepositoryEvidenceResolutionException.class, () -> resolver.resolve(insight));
    }

    // --- 10. deterministicOrder ---
    @Test
    void deterministicOrder() {
        UUID f1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID f2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID f3 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        proposal.setSupportingFactIds(List.of(f3, f1, f2)); // unsorted input

        when(factRepository.findByAnalysisIdAndIdIn(any(), any()))
                .thenReturn(List.of(
                        fact(f1, "zebra.java"),
                        fact(f2, "alpha.java"),
                        fact(f3, "beta.java")));

        var result1 = resolver.resolve(insight);
        var result2 = resolver.resolve(insight);

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(result1.get().resolvedFiles(), result2.get().resolvedFiles());
        // sorted by path asc (deterministic output)
        assertEquals("alpha.java", result1.get().resolvedFiles().get(0).path());
        assertEquals("beta.java", result1.get().resolvedFiles().get(1).path());
        assertEquals("zebra.java", result1.get().resolvedFiles().get(2).path());
    }

    // --- 11. emptyLineage_ReturnsNoLineage ---
    @Test
    void emptyLineage_ReturnsNoLineage() {
        proposal.setSupportingFactIds(List.of());
        proposal.setSupportingObservationIds(List.of());

        var result = resolver.resolve(insight);

        assertTrue(result.isEmpty());
    }

    // --- 12. emptyLineage_WithOnlyObservationIdsEmpty_WhenFactIdsPresent ---
    @Test
    void emptyLineage_WithOnlyObservationIdsEmpty_WhenFactIdsPresent() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));
        proposal.setSupportingObservationIds(List.of());

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1, "src/main/Foo.java")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().resolvedFiles().size());
    }

    // --- 13. baselineAndSource_Propagated ---
    @Test
    void baselineAndSource_Propagated() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1, "src/main/Foo.java")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertEquals(source, result.get().source());
        assertEquals("abc123", result.get().baselineRevision());
    }

    // --- 14. projection_CarriesFactIdPerPath ---
    @Test
    void projection_CarriesFactIdPerPath() {
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1, f2));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1, f2)))
                .thenReturn(List.of(fact(f1, "src/main/Foo.java"), fact(f2, "src/main/Bar.java")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        // find by path to verify factId mapping (order is by path)
        var byPath = result.get().resolvedFiles().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ResolvedFileEvidence::path,
                        ResolvedFileEvidence::factId));
        assertEquals(f1, byPath.get("src/main/Foo.java"));
        assertEquals(f2, byPath.get("src/main/Bar.java"));
    }

    // --- 15. noWrites_Resolver ---
    @Test
    void noWrites_Resolver() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1, "src/main/Foo.java")));

        resolver.resolve(insight);

        verify(factRepository, never()).save(any());
        verify(observationRepository, never()).save(any());
    }

    // --- 16. allRefsExcluded_EmptyResolvedFiles ---
    @Test
    void allRefsExcluded_EmptyResolvedFiles() {
        UUID f1 = UUID.randomUUID();
        proposal.setSupportingFactIds(List.of(f1));

        when(factRepository.findByAnalysisIdAndIdIn(analysisId, Set.of(f1)))
                .thenReturn(List.of(fact(f1,
                        "source:12345",
                        "repository:/path",
                        "git:abc123",
                        "/absolute",
                        "C:/windows",
                        "..")));

        var result = resolver.resolve(insight);

        assertTrue(result.isPresent());
        assertTrue(result.get().resolvedFiles().isEmpty());
    }
}