package com.hopeful117.devlogai.shared.exception.handler;

import com.hopeful117.devlogai.project.controller.ProjectController;
import com.hopeful117.devlogai.project.exception.ProjectSlugAlreadyExistsException;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorHandlingWebMvcTest extends ControllerWebMvcTestSupport {

    private MockMvc mvc;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        mvc = mockMvc(new ProjectController(projectService),
                new ErrorProbeController());
    }

    @Test
    void shouldReturnTraceableNotFoundForUnknownRoute() throws Exception {
        mvc.perform(get("/api/v1/not-a-route")
                        .header("X-Correlation-ID", "route-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-ID", "route-404"))
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/not-a-route"))
                .andExpect(jsonPath("$.correlationId").value("route-404"));
    }

    @Test
    void shouldReturnTraceableMethodNotAllowed() throws Exception {
        mvc.perform(delete("/api/v1/projects")
                        .header("X-Correlation-ID", "method-405"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(header().string("X-Correlation-ID", "method-405"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.correlationId").value("method-405"));
    }

    @Test
    void shouldReturnExplicitErrorsForInvalidAndUnknownProjectIds() throws Exception {
        mvc.perform(get("/api/test/entity/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.correlationId",
                        matchesPattern("[0-9a-f-]{36}")));

        mvc.perform(get("/api/test/entity/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldReturnExplicitValidationAndDuplicateSlugErrors() throws Exception {
        mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Missing name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        when(projectService.create(any())).thenThrow(
                new ProjectSlugAlreadyExistsException(
                        "story-0007-error-contract"));
        mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Story 0007 Error Contract","description":"test"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PROJECT_SLUG_ALREADY_EXISTS"));
    }

    @Test
    void shouldNotExposeUnexpectedExceptionDetails() throws Exception {
        mvc.perform(get("/api/test/error-probe")
                        .header("X-Correlation-ID", "internal-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Correlation-ID", "internal-500"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message")
                        .value(not("sensitive implementation detail")));
    }

    @RestController
    static class ErrorProbeController {

        @GetMapping("/api/test/error-probe")
        void fail() {
            throw new IllegalStateException("sensitive implementation detail");
        }

        @GetMapping("/api/test/entity/{id}")
        void entity(@PathVariable java.util.UUID id) {
            throw new EntityNotFoundException("Project", id);
        }
    }
}
