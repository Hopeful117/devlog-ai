package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class OidcAuthenticationFailureDiagnosticsWebMvcTest {

    @Test
    void emitsDiagnosticAndBearerChallengeForARealBearerAuthenticationFailure() throws Exception {
        AuthenticationManager authenticationManager = authentication -> {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token", "Token expired", null));
        };
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(authenticationManager);
        filter.setAuthenticationEntryPoint(new OidcAuthenticationFailureDiagnostics());

        MockMvc mvc = standaloneSetup(new ProtectedController())
                .addFilters(filter)
                .build();

        mvc.perform(get("/protected").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        OidcAuthenticationFailureDiagnostics.HEADER_NAME, "TOKEN_EXPIRED"))
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.containsString("Bearer")));
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/protected")
        void protectedResource(HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
