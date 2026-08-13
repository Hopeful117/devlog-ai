package com.hopeful117.devlogai.projectstate;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectStateProposalNoiseReducerTest {

    private final ProjectStateProposalNoiseReducer reducer = new ProjectStateProposalNoiseReducer();

    @Test
    void shouldCollapseExactSemanticDuplicatesToOneRepresentative() {
        ValidatableProposal first = proposal(UUID.fromString("00000000-0000-4000-8000-000000000101"),
                "PROJECT_PRESENTATION", "Project Overview", "DevLog AI overview.", "0.80");
        ValidatableProposal stronger = proposal(UUID.fromString("00000000-0000-4000-8000-000000000102"),
                "PROJECT_PRESENTATION", "Project Overview", "DevLog AI overview.", "0.95");

        List<ValidatableProposal> reduced = reducer.reduce(List.of(first, stronger));

        assertEquals(1, reduced.size());
        assertEquals(stronger.getId(), reduced.getFirst().getId());
    }

    @Test
    void shouldPreserveDistinctProposals() {
        ValidatableProposal overview = proposal(UUID.fromString("00000000-0000-4000-8000-000000000201"),
                "PROJECT_PRESENTATION", "Project Overview", "DevLog AI overview.", "0.95");
        ValidatableProposal docker = proposal(UUID.fromString("00000000-0000-4000-8000-000000000202"),
                "TECHNOLOGY_DESCRIPTION", "Use of Docker", "Containerized deployment setup.", "0.90");

        List<ValidatableProposal> reduced = reducer.reduce(List.of(overview, docker));

        assertEquals(2, reduced.size());
    }

    @Test
    void shouldTreatWhitespaceAndCaseVariantsAsDuplicates() {
        ValidatableProposal first = proposal(UUID.fromString("00000000-0000-4000-8000-000000000301"),
                "PROJECT_PRESENTATION", "Project Overview", "DevLog AI overview.", "0.95");
        ValidatableProposal second = proposal(UUID.fromString("00000000-0000-4000-8000-000000000302"),
                "project_presentation", "  project overview  ", "  devlog ai overview.  ", "0.70");

        List<ValidatableProposal> reduced = reducer.reduce(List.of(first, second));

        assertEquals(1, reduced.size());
        assertEquals(first.getId(), reduced.getFirst().getId());
    }

    @Test
    void shouldCollapseNearDuplicateLlMVariants() {
        ValidatableProposal first = proposal(UUID.fromString("00000000-0000-4000-8000-000000000401"),
                "PROJECT_PRESENTATION",
                "Project Overview",
                "The project named devlog-ai is an AI-powered documentation assistant that analyzes Git/GitHub activity to generate technical Markdown articles, preserve engineering decisions, and maintain up-to-date project documentation.",
                "0.95");
        ValidatableProposal second = proposal(UUID.fromString("00000000-0000-4000-8000-000000000402"),
                "PROJECT_PRESENTATION",
                "Project Overview of devlog-ai",
                "devlog-ai is an active project that serves as an AI-powered documentation assistant. It analyzes Git/GitHub activity to generate technical Markdown articles, preserve engineering decisions, and keep project documentation up to date.",
                "0.90");
        ValidatableProposal third = proposal(UUID.fromString("00000000-0000-4000-8000-000000000403"),
                "PROJECT_PRESENTATION",
                "Project Overview",
                "The project named devlog-ai is an active application that functions as an AI-powered documentation assistant. It analyzes Git/GitHub activity to generate technical Markdown articles, capture engineering decisions, and keep project documentation updated.",
                "1.00");

        List<ValidatableProposal> reduced = reducer.reduce(List.of(first, second, third));

        assertEquals(1, reduced.size());
        assertEquals(third.getId(), reduced.getFirst().getId());
    }

    @Test
    void shouldCollapseExactTitleDuplicatesEvenWhenSummariesDiffer() {
        ValidatableProposal first = proposal(UUID.fromString("00000000-0000-4000-8000-000000000501"),
                "TECHNOLOGY_DESCRIPTION",
                "Automated and Integration Testing Infrastructure",
                "The project includes both automated tests and integration test suites, supporting continuous integration and quality assurance processes.",
                "0.90");
        ValidatableProposal second = proposal(UUID.fromString("00000000-0000-4000-8000-000000000502"),
                "TECHNOLOGY_DESCRIPTION",
                "Automated and Integration Testing Infrastructure",
                "The project includes an automated test suite as well as integration tests, facilitating continuous integration and quality control.",
                "1.00");

        List<ValidatableProposal> reduced = reducer.reduce(List.of(first, second));

        assertEquals(1, reduced.size());
        assertEquals(second.getId(), reduced.getFirst().getId());
    }

    private ValidatableProposal proposal(
            UUID id,
            String insightType,
            String title,
            String summary,
            String confidence
    ) {
        return ValidatableProposal.builder()
                .id(id)
                .type(ProposalType.INSIGHT)
                .status(ProposalStatus.PROPOSED)
                .confidence(new BigDecimal(confidence))
                .payload(Map.of(
                        "insightType", insightType,
                        "title", title,
                        "summary", summary
                ))
                .build();
    }
}
