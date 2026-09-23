package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvidenceResolutionControllerWebMvcTest extends ControllerWebMvcTestSupport {
    private static final String REFERENCE =
            "git:dddd1111-2222-3333-4444-555555555555:3cd3723206eae38d518eb696a1dd50c0476264d0";
    private static final UUID SOURCE_ID =
            UUID.fromString("dddd1111-2222-3333-4444-555555555555");

    private final EvidenceResolutionFacade facade = mock(EvidenceResolutionFacade.class);
    private final MockMvc mvc = mockMvc(new EvidenceResolutionController(facade));

    @Test
    void shouldProjectCoreResultWithoutReinterpretingReference() throws Exception {
        EvidenceResolutionResult result = new EvidenceResolutionResult(
                new EvidenceResolutionMetadata(
                        REFERENCE, EvidenceResolutionFamily.GIT_COMMIT, SOURCE_ID,
                        "project-commit", "3cd3723206eae38d518eb696a1dd50c0476264d0",
                        EvidenceResolutionMode.CURRENT, false),
                new GitCommitResolutionPayload(
                        "3cd3723206eae38d518eb696a1dd50c0476264d0", "subject", "message",
                        "author", "author@example.com", Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"), false, false, List.of(), List.of()));
        when(facade.resolve(argThat(request ->
                REFERENCE.equals(request.reference())
                        && request.mode() == EvidenceResolutionMode.CURRENT
                        && request.analysisId() == null))).thenReturn(result);

        mvc.perform(get("/api/v1/evidence/resolve")
                        .param("reference", REFERENCE)
                        .param("mode", "CURRENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.canonicalReference").value(REFERENCE))
                .andExpect(jsonPath("$.metadata.mode").value("CURRENT"))
                .andExpect(jsonPath("$.payload.commitHash")
                        .value("3cd3723206eae38d518eb696a1dd50c0476264d0"));

        verify(facade).resolve(argThat(request -> REFERENCE.equals(request.reference())));
    }

    @Test
    void shouldProjectResolutionFailureWithStableFailureCode() throws Exception {
        when(facade.resolve(argThat(request -> REFERENCE.equals(request.reference()))))
                .thenThrow(new EvidenceResolutionException(
                        EvidenceResolutionFailureCode.AMBIGUOUS_SOURCE,
                        REFERENCE, SOURCE_ID, "two active sources"));

        mvc.perform(get("/api/v1/evidence/resolve")
                        .param("reference", REFERENCE)
                        .param("mode", "CURRENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_RESOLUTION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("AMBIGUOUS_SOURCE")));
    }

    @Test
    void shouldRejectBlankReferenceAsClientError() throws Exception {
        mvc.perform(get("/api/v1/evidence/resolve")
                        .param("reference", " ")
                        .param("mode", "CURRENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
