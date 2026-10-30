package com.hopeful117.devlogai.aiuserconfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AiUserConfigurationPostgresIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationPersistsOnlyNonSecretConfigurationAndEnforcesOneRowPerPrincipal() {
        UUID id = UUID.randomUUID();
        String principal = "principal-" + UUID.randomUUID();
        insert(id, principal, "{}");

        assertThat(jdbc.queryForObject(
                "select allowed_parameters from ai_user_configurations where id = ?",
                String.class, id)).isEqualTo("{}");
        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class)).isEqualTo("61");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'ai_user_configurations'
                  and column_name in ('api_key', 'token', 'credential_handle', 'oauth_subject')
                """, Integer.class)).isZero();

        assertThatThrownBy(() -> insert(UUID.randomUUID(), principal, "{}"))
                .hasMessageContaining("uk_ai_user_configuration_principal");

        assertThatThrownBy(() -> insert(UUID.randomUUID(), "principal-" + UUID.randomUUID(),
                "{\"parameter\":\"value\"}"))
                .hasMessageContaining("ck_ai_user_configuration_parameters_empty");
    }

    private void insert(UUID id, String principal, String parameters) {
        jdbc.update("""
                insert into ai_user_configurations
                    (id, principal_id, provider, authentication_method, selected_model,
                     model_selection_origin, allowed_parameters, created_at, updated_at)
                values (?, ?, 'mock', 'NONE', 'deterministic-v1', 'RECOMMENDED',
                        cast(? as jsonb), ?, ?)
                """, id, principal, parameters,
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
