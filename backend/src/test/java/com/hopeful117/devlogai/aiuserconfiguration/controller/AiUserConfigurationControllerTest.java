package com.hopeful117.devlogai.aiuserconfiguration.controller;

import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiModelSelectionOrigin;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiUserConfigurationState;
import com.hopeful117.devlogai.aiuserconfiguration.service.AiUserConfigurationService;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiUserConfigurationControllerTest {
    private final AiUserConfigurationService service = mock(AiUserConfigurationService.class);
    private final AuthenticatedPrincipalResolver principalResolver = mock(AuthenticatedPrincipalResolver.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AiUserConfigurationController(service, principalResolver))
            .build();

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        when(principalResolver.resolve(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AiUserConfigurationController(service, principalResolver)
                .get(new MockHttpServletRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void returnsNotConfiguredForAuthenticatedPrincipalWithoutConfiguration() throws Exception {
        when(principalResolver.resolve(any())).thenReturn(Optional.of(principal()));
        when(service.find(any())).thenReturn(Optional.empty());
        when(service.state(Optional.empty())).thenReturn(AiUserConfigurationState.NOT_CONFIGURED);

        mvc.perform(get("/api/v1/ai/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.credentialHandle").doesNotExist());
    }

    @Test
    void writesOnlyNonSecretConfigurationFields() throws Exception {
        when(principalResolver.resolve(any())).thenReturn(Optional.of(principal()));
        var configuration = new AiUserConfiguration();
        configuration.setProvider("mock");
        configuration.setAuthenticationMethod(AiAuthenticationMethod.NONE);
        configuration.setSelectedModel("deterministic-v1");
        configuration.setModelSelectionOrigin(AiModelSelectionOrigin.RECOMMENDED);
        configuration.setAllowedParameters(Map.of());
        when(service.save(any(), any(), any(), any(), any(), any())).thenReturn(configuration);
        when(service.state(any())).thenReturn(AiUserConfigurationState.READY);

        mvc.perform(put("/api/v1/ai/configuration")
                        .contentType("application/json")
                        .content("""
                                {"provider":"mock","authenticationMethod":"NONE",
                                 "selectedModel":"deterministic-v1","modelSelectionOrigin":"RECOMMENDED",
                                 "allowedParameters":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.state").value("READY"))
                .andExpect(jsonPath("$.credentialHandle").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void deletesAuthenticatedPrincipalConfiguration() throws Exception {
        when(principalResolver.resolve(any())).thenReturn(Optional.of(principal()));

        mvc.perform(delete("/api/v1/ai/configuration"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsMalformedConfigurationRequests() throws Exception {
        when(principalResolver.resolve(any())).thenReturn(Optional.of(principal()));

        mvc.perform(put("/api/v1/ai/configuration")
                        .contentType("application/json")
                        .content("{\"provider\":\"mock\"}"))
                .andExpect(status().isBadRequest());
    }

    private static AuthenticatedPrincipal principal() {
        return new AuthenticatedPrincipal("principal-1", PrincipalKind.HUMAN, "issuer");
    }
}
