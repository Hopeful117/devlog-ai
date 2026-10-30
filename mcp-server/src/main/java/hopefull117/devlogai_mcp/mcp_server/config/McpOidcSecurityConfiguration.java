package hopefull117.devlogai_mcp.mcp_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${devlog.security.oidc.issuer-uri:}')")
public class McpOidcSecurityConfiguration {
    @Bean
    SecurityFilterChain mcpOidcSecurityFilterChain(
            HttpSecurity http,
            @Value("${devlog.security.oidc.issuer-uri}") String issuer,
            @Value("${devlog.security.oidc.audience:}") String audience,
            @Value("${devlog.security.oidc.jwks-uri:}") String jwksUri) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder(issuer, audience, jwksUri))))
                .build();
    }

    @Bean
    NimbusJwtDecoder mcpJwtDecoder(
            @Value("${devlog.security.oidc.issuer-uri}") String issuer,
            @Value("${devlog.security.oidc.audience:}") String audience,
            @Value("${devlog.security.oidc.jwks-uri:}") String jwksUri) {
        return jwtDecoder(issuer, audience, jwksUri);
    }

    private NimbusJwtDecoder jwtDecoder(String issuer, String audience, String jwksUri) {
        NimbusJwtDecoder decoder = jwksUri == null || jwksUri.isBlank()
                ? (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer)
                : NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        if (audience == null || audience.isBlank()) {
            decoder.setJwtValidator(issuerValidator);
        } else {
            OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                    jwt.getAudience().contains(audience)
                            ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                            : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error(
                                    "invalid_token", "Missing configured audience", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        }
        return decoder;
    }
}
