package hopefull117.devlogai_mcp.mcp_server.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FailClosedMcpAuthenticatedPrincipalProvider implements McpAuthenticatedPrincipalProvider {
    private final String issuer;
    private final String configuredBearerToken;
    private final ObjectProvider<JwtDecoder> jwtDecoder;

    @Autowired
    public FailClosedMcpAuthenticatedPrincipalProvider(
            @Value("${devlog.security.oidc.issuer-uri:}") String issuer,
            @Value("${devlog.security.oidc.bearer-token:}") String configuredBearerToken,
            ObjectProvider<JwtDecoder> jwtDecoder) {
        this.issuer = issuer == null ? "" : issuer.trim();
        this.configuredBearerToken = configuredBearerToken == null ? "" : configuredBearerToken.trim();
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Optional<McpPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return configuredPrincipal();
        }
        String bearerToken = bearer(authentication.getCredentials());
        if (bearerToken != null) return principalFromBearer(bearerToken);
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return principalFromJwt(jwt, "Bearer " + jwt.getTokenValue());
        }
        return configuredPrincipal();
    }

    @Override
    public Optional<McpPrincipal> currentPrincipal(McpSyncRequestContext requestContext) {
        if (requestContext != null) {
            Object raw = requestContext.transportContext().get("authorization");
            if (raw instanceof String authorization && authorization.startsWith("Bearer ")) {
                return principalFromBearer(authorization);
            }
        }
        return currentPrincipal();
    }

    private String bearer(Object credentials) {
        return credentials instanceof String value && value.startsWith("Bearer ") ? value : null;
    }

    private Optional<McpPrincipal> configuredPrincipal() {
        if (configuredBearerToken.isBlank()) return Optional.empty();
        String authorization = configuredBearerToken.startsWith("Bearer ")
                ? configuredBearerToken : "Bearer " + configuredBearerToken;
        return principalFromBearer(authorization);
    }

    private Optional<McpPrincipal> principalFromBearer(String authorization) {
        if (issuer.isBlank()) return Optional.empty();
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) return Optional.empty();
        try {
            JwtDecoder decoder = jwtDecoder.getIfAvailable(() -> null);
            if (decoder == null) {
                log.warn("MCP bearer validation unavailable because no JwtDecoder is configured");
                return Optional.empty();
            }
            return principalFromJwt(decoder.decode(token), "Bearer " + token);
        } catch (RuntimeException exception) {
            log.warn("MCP bearer validation failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<McpPrincipal> principalFromJwt(Jwt jwt, String authorization) {
        if (jwt == null || jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())) {
            log.warn("MCP bearer rejected because issuer does not match configured issuer");
            return Optional.empty();
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            log.warn("MCP bearer rejected because subject is blank");
            return Optional.empty();
        }
        return Optional.of(new McpPrincipal(jwt.getSubject(), PrincipalKind.HUMAN, issuer, authorization));
    }
}
