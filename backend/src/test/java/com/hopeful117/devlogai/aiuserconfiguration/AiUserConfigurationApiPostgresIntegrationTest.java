package com.hopeful117.devlogai.aiuserconfiguration;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.authorization.RequestAuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.aiuserconfiguration.controller.AiUserConfigurationController;
import com.hopeful117.devlogai.aiuserconfiguration.service.AiUserConfigurationService;
import com.hopeful117.devlogai.shared.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class AiUserConfigurationApiPostgresIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiUserConfigurationService service;

    @Autowired
    private AuthenticatedPrincipalResolver principalResolver;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AiUserConfigurationController(service, principalResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void persistsCrudAndKeepsPrincipalConfigurationsIsolated() throws Exception {
        putConfiguration("principal-1");
        putConfiguration("principal-2");

        putUpdatedConfiguration("principal-1");
        getConfiguration("principal-1", "READY", "deterministic-v1", "EXPLICIT");
        getConfiguration("principal-2", "READY");

        mvc.perform(delete("/api/v1/ai/configuration").with(request -> principal(request, "principal-1")))
                .andExpect(status().isNoContent());
        getConfiguration("principal-1", "NOT_CONFIGURED");
        getConfiguration("principal-2", "READY");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from ai_user_configurations", Integer.class)).isEqualTo(1);
    }

    @Test
    void exposesNonDisclosureHttpErrorsThroughTheApplicationAdvice() throws Exception {
        mvc.perform(get("/api/v1/ai/configuration"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(put("/api/v1/ai/configuration")
                        .with(request -> principal(request, "principal-1"))
                        .contentType("application/json")
                        .content("{\"provider\":\"mock\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(put("/api/v1/ai/configuration")
                        .with(request -> principal(request, "principal-1"))
                        .contentType("application/json")
                        .content("{\"provider\":\"mock\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mvc.perform(put("/api/v1/ai/configuration")
                        .with(request -> principal(request, "principal-1"))
                        .contentType("application/json")
                        .content("""
                                {"provider":"unknown","authenticationMethod":"NONE",
                                 "selectedModel":"unknown","modelSelectionOrigin":"EXPLICIT",
                                 "allowedParameters":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private void putConfiguration(String principalId) throws Exception {
        mvc.perform(put("/api/v1/ai/configuration")
                        .with(request -> principal(request, principalId))
                        .contentType("application/json")
                        .content("""
                                {"provider":"mock","authenticationMethod":"NONE",
                                 "selectedModel":"deterministic-v1","modelSelectionOrigin":"RECOMMENDED",
                                 "allowedParameters":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.credentialHandle").doesNotExist());
    }

    private void putUpdatedConfiguration(String principalId) throws Exception {
        mvc.perform(put("/api/v1/ai/configuration")
                        .with(request -> principal(request, principalId))
                        .contentType("application/json")
                        .content("""
                                {"provider":"mock","authenticationMethod":"NONE",
                                 "selectedModel":"deterministic-v1","modelSelectionOrigin":"EXPLICIT",
                                 "allowedParameters":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedModel").value("deterministic-v1"));
    }

    private void getConfiguration(String principalId, String state) throws Exception {
        mvc.perform(get("/api/v1/ai/configuration")
                        .with(request -> principal(request, principalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(state));
    }

    private void getConfiguration(String principalId, String state, String selectedModel,
                                  String selectionOrigin) throws Exception {
        mvc.perform(get("/api/v1/ai/configuration")
                        .with(request -> principal(request, principalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(state))
                .andExpect(jsonPath("$.selectedModel").value(selectedModel))
                .andExpect(jsonPath("$.modelSelectionOrigin").value(selectionOrigin));
    }

    private static MockHttpServletRequest principal(
            MockHttpServletRequest request, String principalId) {
        AuthenticatedPrincipal authenticated =
                new AuthenticatedPrincipal(principalId, PrincipalKind.HUMAN, "test-issuer");
        request.setUserPrincipal(new TestCoreRequestPrincipal(authenticated));
        return request;
    }

    private record TestCoreRequestPrincipal(AuthenticatedPrincipal authenticatedPrincipal)
            implements RequestAuthenticatedPrincipalResolver.CoreRequestPrincipal {
        @Override
        public String getName() {
            return authenticatedPrincipal.principalId();
        }
    }
}
