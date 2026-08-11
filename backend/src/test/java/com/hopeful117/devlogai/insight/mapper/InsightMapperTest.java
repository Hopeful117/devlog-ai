package com.hopeful117.devlogai.insight.mapper;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InsightMapperTest {

    private final InsightMapper mapper = new InsightMapperImpl();

    @Test
    void mapsAllSemanticFields() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID validationId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();

        Insight insight = Insight.builder()
                .id(UUID.randomUUID())
                .project(Project.builder().id(projectId).build())
                .analysis(Analysis.builder().id(analysisId).build())
                .proposal(ValidatableProposal.builder().id(proposalId).build())
                .validation(Validation.builder().id(validationId).build())
                .type(InsightType.ARCHITECTURAL)
                .severity(InsightSeverity.WARNING)
                .title("Modular architecture")
                .content("The application is split into bounded modules.")
                .rationale("Boundaries keep modules independently deployable.")
                .confidence(new BigDecimal("0.9200"))
                .evidenceReferences(List.of("src/main/java/com/example/App.java"))
                .sourceType("ARCHITECTURE_DESCRIPTION")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        InsightResponse response = mapper.toResponse(insight);

        assertEquals(projectId, response.projectId());
        assertEquals(analysisId, response.analysisId());
        assertEquals(proposalId, response.proposalId());
        assertEquals(validationId, response.validationId());
        assertEquals(InsightType.ARCHITECTURAL, response.type());
        assertEquals(InsightSeverity.WARNING, response.severity());
        assertEquals("Modular architecture", response.title());
        assertEquals("The application is split into bounded modules.", response.content());
        assertEquals("Boundaries keep modules independently deployable.", response.rationale());
        assertEquals(new BigDecimal("0.9200"), response.confidence());
        assertEquals(List.of("src/main/java/com/example/App.java"), response.evidenceReferences());
        assertEquals("ARCHITECTURE_DESCRIPTION", response.sourceType());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void mapsNullableSemanticFields() {
        Insight insight = Insight.builder()
                .project(Project.builder().build())
                .analysis(Analysis.builder().build())
                .proposal(ValidatableProposal.builder().build())
                .validation(Validation.builder().build())
                .type(InsightType.TECHNOLOGY)
                .severity(InsightSeverity.INFO)
                .title("Stack")
                .content("The project uses Spring Boot.")
                .build();

        InsightResponse response = mapper.toResponse(insight);

        assertEquals(null, response.rationale());
        assertEquals(null, response.confidence());
        assertEquals(List.of(), response.evidenceReferences());
        assertEquals(null, response.sourceType());
    }
}
