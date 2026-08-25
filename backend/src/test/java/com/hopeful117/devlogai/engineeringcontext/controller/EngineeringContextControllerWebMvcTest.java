package com.hopeful117.devlogai.engineeringcontext.controller;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectNote;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EngineeringContextControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private EngineeringContextFacade facade;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(EngineeringContextFacade.class);
        mvc = mockMvc(new EngineeringContextController(facade));
    }

    @Test
    void shouldReturnEngineeringContextWithIntent() throws Exception {
        var projectContext = new ProjectContext(
                UUID.fromString("c2cbc6d0-8c49-461a-9b8c-0d4f5b6e7a8f"),
                "devlog-ai",
                "devlog-ai",
                "DevLog AI - Engineering project tracking",
                ProjectStatus.ACTIVE.name(),
                List.of(
                        new ProjectNote(
                                UUID.randomUUID(),
                                "CONSTRAINT",
                                "Technical constraints",
                                "- Java 17\n- PostgreSQL 15+\n- Kubernetes 1.27+\n- Git repository with adr/ directory",
                                "ACTIVE",
                                Instant.now()
                        )
                )
        );

        var evidence = List.of(
                new EngineeringEvidence(
                        "CHANGED_FILE",
                        "COMMIT_DIFF",
                        "Modified project-context-inputs-section.html to use ngx-markdown renderer",
                        "GIT",
                        "project-context-inputs-section.html",
                        "a1b2c3d4e5f67890abcdef1234567890abcdef12",
                        95,
                        "SELECTED_BY_RANK",
                        Instant.parse("2026-08-20T10:00:00Z"),
                        List.of("diff:a1b2c3d4e5f67890abcdef1234567890abcdef12:project-context-inputs-section.html"),
                        Map.of("collectorId", "commit-diff", "collectorVersion", "v1"),
                        null,
                        null,
                        "devlog://projects/devlog-ai/commits/a1b2c3d4e5f67890abcdef1234567890abcdef12"
                )
        );

        var metadata = new EngineeringContextMetadata(
                42,
                15,
                false,
                2100,
                "deadbeef1234567890deadbeef1234567890deadbeef",
                List.of("EVIDENCE_SUMMARY_TRUNCATED"),
                null
        );

        var engineeringContext = new EngineeringContext(
                projectContext,
                "Investigate why Project Notes Markdown is displayed incorrectly.",
                evidence,
                metadata
        );

        when(facade.getEngineeringContext(
                        "devlog-ai",
                        "Investigate why Project Notes Markdown is displayed incorrectly."
                ))
                .thenReturn(engineeringContext);

        mvc.perform(
                        MockMvcRequestBuilders.get(
                                "/api/v1/projects/devlog-ai/engineering-context")
                                .queryParam("intent",
                                        "Investigate why Project Notes Markdown is displayed incorrectly.")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.slug").value("devlog-ai"))
                .andExpect(jsonPath("$.project.name").value("devlog-ai"))
                .andExpect(jsonPath("$.intent").value(
                        "Investigate why Project Notes Markdown is displayed incorrectly."))
                .andExpect(jsonPath("$.evidence[0].kind").value("CHANGED_FILE"))
                .andExpect(jsonPath("$.evidence[0].summary")
                        .value("Modified project-context-inputs-section.html to use ngx-markdown renderer"))
                .andExpect(jsonPath("$.evidence[0].sourceType").value("GIT"))
                .andExpect(jsonPath("$.evidence[0].relevanceScore").value(95))
                .andExpect(jsonPath("$.evidence[0].selectionReason").value("SELECTED_BY_RANK"))
                .andExpect(jsonPath("$.evidence[0].occurredAt").value("2026-08-20T10:00:00Z"))
                .andExpect(jsonPath("$.evidence[0].relatedReferences.length()").value(1))
                .andExpect(jsonPath("$.evidence[0].extractionMetadata.collectorId")
                        .value("commit-diff"))
                .andExpect(jsonPath("$.evidence[0].content").doesNotExist())
                .andExpect(jsonPath("$.evidence[0].symbols").doesNotExist())
                .andExpect(jsonPath("$.evidence[0].resource")
                        .value("devlog://projects/devlog-ai/commits/a1b2c3d4e5f67890abcdef1234567890abcdef12"))
                .andExpect(jsonPath("$.metadata.candidateCount").value(42))
                .andExpect(jsonPath("$.metadata.selectedCount").value(15))
                .andExpect(jsonPath("$.metadata.truncated").value(false))
                .andExpect(jsonPath("$.metadata.warnings[0]")
                        .value("EVIDENCE_SUMMARY_TRUNCATED"));
    }

    @Test
    void shouldReturnStatus500WhenIntentQueryParameterIsMissing() throws Exception {
        mvc.perform(
                        MockMvcRequestBuilders.get(
                                "/api/v1/projects/devlog-ai/engineering-context"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldCallFacadeWithCorrectArguments() throws Exception {
        mvc.perform(
                        MockMvcRequestBuilders.get(
                                "/api/v1/projects/devlog-ai/engineering-context")
                                .queryParam("intent",
                                        "Investigate why Project Notes Markdown is displayed incorrectly."));

        verify(facade).getEngineeringContext(
                "devlog-ai",
                "Investigate why Project Notes Markdown is displayed incorrectly.");
    }
}