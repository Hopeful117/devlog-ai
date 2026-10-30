package com.hopeful117.devlogai.authorization;

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
public class OidcSecurityConfiguration {
    @Bean
    SecurityFilterChain oidcSecurityFilterChain(
            HttpSecurity http,
            @Value("${devlog.security.oidc.issuer-uri}") String issuer,
            @Value("${devlog.security.oidc.audience:}") String audience) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/story-context-agent/tasks/*/callback").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder(issuer, audience))))
                .build();
    }

    @Bean
    NimbusJwtDecoder jwtDecoder(
            @Value("${devlog.security.oidc.issuer-uri}") String issuer,
            @Value("${devlog.security.oidc.audience:}") String audience) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);
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
