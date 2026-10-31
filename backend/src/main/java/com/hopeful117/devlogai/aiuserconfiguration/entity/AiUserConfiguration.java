package com.hopeful117.devlogai.aiuserconfiguration.entity;

import com.hopeful117.devlogai.aiuserconfiguration.model.AiModelSelectionOrigin;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiConfigurationTestStatus;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_user_configurations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUserConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "principal_id", nullable = false, length = 255, unique = true)
    private String principalId;

    @Column(nullable = false, length = 100)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_method", nullable = false, length = 32)
    private AiAuthenticationMethod authenticationMethod;

    @Column(name = "selected_model", nullable = false, length = 255)
    private String selectedModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_selection_origin", nullable = false, length = 16)
    private AiModelSelectionOrigin modelSelectionOrigin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> allowedParameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_configuration_test_status", length = 32)
    private AiConfigurationTestStatus lastConfigurationTestStatus;

    @Column(name = "last_configuration_test_id")
    private UUID lastConfigurationTestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
