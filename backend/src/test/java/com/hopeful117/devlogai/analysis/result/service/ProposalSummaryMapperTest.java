package com.hopeful117.devlogai.analysis.result.service;

import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProposalSummaryMapperTest {

    private final ProposalSummaryMapper mapper = new ProposalSummaryMapper();

    @Test
    void shouldExtractInsightFields() {
        ValidatableProposal proposal = proposal(ProposalType.INSIGHT, Map.of(
                "title", "Architecture Layering Insight",
                "summary", "Clean separation improves testability",
                "rationale", "Because components are independent",
                "insightType", "ARCHITECTURE",
                "deltaType", "CONFIRMATION"
        ));

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertEquals("Architecture Layering Insight", summary.title());
        assertEquals("Clean separation improves testability", summary.summary());
        assertEquals("Because components are independent", summary.rationale());
        assertEquals("ARCHITECTURE", summary.insightType());
        assertEquals("CONFIRMATION", summary.deltaType());
        assertNull(summary.context());
        assertNull(summary.choice());
        assertNull(summary.consequences());
        assertNull(summary.category());
        assertNull(summary.significance());
    }

    @Test
    void shouldExtractDecisionFields() {
        ValidatableProposal proposal = proposal(ProposalType.ENGINEERING_DECISION, Map.of(
                "title", "Database Choice Decision",
                "summary", "PostgreSQL selected",
                "rationale", "Need ACID compliance",
                "context", "Multiple DB options considered",
                "choice", "PostgreSQL",
                "consequences", "Migration from SQLite required"
        ));

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertEquals("Database Choice Decision", summary.title());
        assertEquals("PostgreSQL selected", summary.summary());
        assertEquals("Need ACID compliance", summary.rationale());
        assertEquals("Multiple DB options considered", summary.context());
        assertEquals("PostgreSQL", summary.choice());
        assertEquals("Migration from SQLite required", summary.consequences());
        assertNull(summary.insightType());
        assertNull(summary.deltaType());
        assertNull(summary.category());
        assertNull(summary.significance());
    }

    @Test
    void shouldExtractEventFields() {
        ValidatableProposal proposal = proposal(ProposalType.ENGINEERING_EVENT, Map.of(
                "title", "Schema Migration Completed",
                "summary", "Database migrated to v2",
                "category", "SCHEMA_CHANGE",
                "significance", "HIGH"
        ));

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertEquals("Schema Migration Completed", summary.title());
        assertEquals("Database migrated to v2", summary.summary());
        assertEquals("SCHEMA_CHANGE", summary.category());
        assertEquals("HIGH", summary.significance());
        assertNull(summary.rationale());
        assertNull(summary.insightType());
        assertNull(summary.deltaType());
        assertNull(summary.context());
        assertNull(summary.choice());
        assertNull(summary.consequences());
    }

    @Test
    void shouldReturnNullTypeSpecificFieldsForUnknownType() {
        ValidatableProposal proposal = proposal(ProposalType.CHALLENGE, Map.of(
                "title", "Test Challenge",
                "summary", "Testing"
        ));

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertNull(summary.rationale());
        assertNull(summary.insightType());
        assertNull(summary.deltaType());
        assertNull(summary.context());
        assertNull(summary.choice());
        assertNull(summary.consequences());
        assertNull(summary.category());
        assertNull(summary.significance());
    }

    @Test
    void shouldPreserveGroundingIdsAsSeparateLists() {
        UUID factId = UUID.randomUUID();
        UUID obsId = UUID.randomUUID();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .id(UUID.randomUUID())
                .type(ProposalType.INSIGHT)
                .status(ProposalStatus.ACCEPTED)
                .payload(Map.of("title", "Test", "summary", "Test"))
                .confidence(BigDecimal.valueOf(0.9))
                .supportingFactIds(List.of(factId))
                .supportingObservationIds(List.of(obsId))
                .createdAt(Instant.now())
                .build();

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertEquals(List.of(factId), summary.supportingFactIds());
        assertEquals(List.of(obsId), summary.supportingObservationIds());
    }

    @Test
    void shouldDefaultEmptyGroundingLists() {
        ValidatableProposal proposal = proposal(ProposalType.INSIGHT, Map.of("title", "T", "summary", "S"));

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertNotNull(summary.supportingFactIds());
        assertTrue(summary.supportingFactIds().isEmpty());
        assertNotNull(summary.supportingObservationIds());
        assertTrue(summary.supportingObservationIds().isEmpty());
    }

    @Test
    void shouldBuildEvidencePreviewFromGroundingIds() {
        UUID factId = UUID.randomUUID();
        UUID obsId = UUID.randomUUID();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .id(UUID.randomUUID())
                .type(ProposalType.INSIGHT)
                .status(ProposalStatus.ACCEPTED)
                .payload(Map.of("title", "T", "summary", "S"))
                .supportingFactIds(List.of(factId))
                .supportingObservationIds(List.of(obsId))
                .createdAt(Instant.now())
                .build();

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertFalse(summary.evidencePreview().isEmpty());
        assertTrue(summary.evidencePreview().get(0).startsWith("Fact#"));
        assertTrue(summary.evidencePreview().get(1).startsWith("Observation#"));
    }

    @Test
    void shouldDefaultTitleAndSummaryWhenMissing() {
        ValidatableProposal proposal = proposal(ProposalType.INSIGHT, Map.of());

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, null);

        assertEquals("Untitled proposal", summary.title());
        assertEquals("", summary.summary());
    }

    @Test
    void shouldPassThroughTrustedArtifact() {
        AnalysisResultResponse.TrustedArtifact artifact = new AnalysisResultResponse.TrustedArtifact(
                UUID.randomUUID(),
                AnalysisResultResponse.TrustedArtifactType.INSIGHT,
                AnalysisResultResponse.TrustedArtifactAvailability.AVAILABLE,
                true
        );
        ValidatableProposal proposal = proposal(ProposalType.INSIGHT, Map.of("title", "T", "summary", "S"));

        AnalysisResultResponse.ProposalSummary summary = mapper.mapToSummary(proposal, artifact);

        assertEquals(artifact, summary.trustedArtifact());
    }

    private ValidatableProposal proposal(ProposalType type, Map<String, Object> payload) {
        return ValidatableProposal.builder()
                .id(UUID.randomUUID())
                .type(type)
                .status(ProposalStatus.ACCEPTED)
                .payload(payload)
                .confidence(BigDecimal.valueOf(0.85))
                .createdAt(Instant.now())
                .build();
    }
}
