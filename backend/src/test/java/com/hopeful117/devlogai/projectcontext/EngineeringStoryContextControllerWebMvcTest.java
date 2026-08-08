package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EngineeringStoryContextControllerWebMvcTest
        extends ControllerWebMvcTestSupport {

    private static final String PATH =
            "/api/projects/{projectId}/engineering-story-context";

    private MockMvc mvc;
    private EngineeringStoryContextService service;

    @BeforeEach
    void setUp() {
        service = mock(EngineeringStoryContextService.class);
        mvc = mockMvc(new EngineeringStoryContextController(service));
    }

    @Test
    void shouldBuildContextFromPostBody() throws Exception {
        UUID projectId = UUID.randomUUID();
        String description = "Add a body-based context request";
        when(service.buildWithRepositoryContext(projectId, description))
                .thenReturn(context(projectId));

        mvc.perform(post(PATH, projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.generatedAt").value("2026-08-08T12:00:00Z"))
                .andExpect(jsonPath("$.repositoryContext.diagnostics.candidatesByKind.TEST_FILE")
                        .value(4))
                .andExpect(jsonPath("$.repositoryContext.diagnostics.preferredLayerAvailability[0].reason")
                        .value("NO_CANDIDATE_FOR_PREFERRED_LAYER"))
                .andExpect(jsonPath("$.repositoryContext.evidence[0].content.status")
                        .value("TRUNCATED"))
                .andExpect(jsonPath("$.repositoryContext.evidence[0].content.text")
                        .value("class App"))
                .andExpect(jsonPath("$.repositoryContext.evidence[1].content")
                        .doesNotExist());

        verify(service).buildWithRepositoryContext(projectId, description);
    }

    @Test
    void shouldTransmitLargeStoryWithoutTruncation() throws Exception {
        UUID projectId = UUID.randomUUID();
        String description = "Engineering Story acceptance criterion. ".repeat(320);
        when(service.buildWithRepositoryContext(projectId, description))
                .thenReturn(context(projectId));

        mvc.perform(post(PATH, projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        verify(service).buildWithRepositoryContext(
                org.mockito.ArgumentMatchers.eq(projectId), captured.capture());
        assertEquals(description, captured.getValue());
        assertEquals(description.length(), captured.getValue().length());
    }

    @Test
    void shouldPreserveGetCompatibility() throws Exception {
        UUID projectId = UUID.randomUUID();
        String description = "Short description";
        when(service.buildWithRepositoryContext(projectId, description))
                .thenReturn(context(projectId));

        mvc.perform(get(PATH, projectId).param("description", description))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()));

        verify(service).buildWithRepositoryContext(projectId, description);
    }

    @Test
    void shouldPreserveMissingNullAndBlankDescriptionSemantics() throws Exception {
        UUID missingProject = UUID.randomUUID();
        UUID nullProject = UUID.randomUUID();
        UUID blankProject = UUID.randomUUID();
        when(service.buildWithRepositoryContext(missingProject, null))
                .thenReturn(context(missingProject));
        when(service.buildWithRepositoryContext(nullProject, null))
                .thenReturn(context(nullProject));
        when(service.buildWithRepositoryContext(blankProject, "   "))
                .thenReturn(context(blankProject));

        mvc.perform(post(PATH, missingProject)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post(PATH, nullProject)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":null}"))
                .andExpect(status().isOk());
        mvc.perform(post(PATH, blankProject)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"   \"}"))
                .andExpect(status().isOk());

        verify(service).buildWithRepositoryContext(missingProject, null);
        verify(service).buildWithRepositoryContext(nullProject, null);
        verify(service).buildWithRepositoryContext(blankProject, "   ");
    }

    @Test
    void shouldReturnCommonErrorsForInvalidBodiesAndMediaType() throws Exception {
        UUID projectId = UUID.randomUUID();

        mvc.perform(post(PATH, projectId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(PATH, projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(PATH, projectId)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("story")
                        .header("X-Correlation-ID", "story-media-415"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("X-Correlation-ID", "story-media-415"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.correlationId").value("story-media-415"));
    }

    @Test
    void shouldUseSharedErrorsForInvalidAndUnknownProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(service.buildWithRepositoryContext(projectId, "story"))
                .thenThrow(new EntityNotFoundException("Project", projectId));

        mvc.perform(post(PATH, "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"story\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(post(PATH, projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"story\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    private EngineeringStoryContext context(UUID projectId) {
        var diagnostics = new RepositoryContextDiagnostics(
                Map.of(RepositoryContextLayer.RELATED_SOURCE_CODE, 4),
                Map.of("TEST_FILE", 4), Map.of("TEST_FILE", 1),
                List.of(new RepositoryContextDiagnostics.PreferredLayerAvailability(
                        RepositoryContextLayer.ADR, false,
                        "NO_CANDIDATE_FOR_PREFERRED_LAYER")), 4, 0);
        var sourceEvidence = new RepositoryEvidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/App.java", "src/App.java", Instant.EPOCH,
                EvidenceScore.unscored(), List.of(),
                new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY_STRUCTURE", "source", "src/App.java", "id"),
                Map.of("collectorId", "repository-structure"), 10, List.of(),
                new RepositoryEvidenceContent(
                        RepositoryEvidenceContent.Status.TRUNCATED, "class App",
                        "CONTENT_TRUNCATED", "selected-file-content", "v1", "abc"));
        var configEvidence = new RepositoryEvidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "CONFIG_FILE",
                "config:pom.xml", "pom.xml", Instant.EPOCH,
                EvidenceScore.unscored(), List.of(),
                new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY_STRUCTURE", "source", "pom.xml", "config"),
                Map.of("collectorId", "repository-structure"), 10, List.of());
        var repositoryContext = new RepositoryContext("v1",
                ContextProfile.ENGINEERING_STORY, List.of("engineering-story-v1"),
                "context-intelligence-v2", List.of(),
                List.of(sourceEvidence, configEvidence),
                Map.of(RepositoryContextLayer.RELATED_SOURCE_CODE, 2), diagnostics,
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                20, 4, 2, true, List.of(), List.of(), "digest");
        return new EngineeringStoryContext(
                null, Instant.parse("2026-08-08T12:00:00Z"), projectId,
                repositoryContext);
    }
}
