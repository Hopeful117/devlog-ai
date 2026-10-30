package com.hopeful117.devlogai.aicredentialbinding.entity;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ai_credential_bindings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_credential_binding_task_provider",
                columnNames = {"ai_task_id", "provider"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCredentialBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_task_id", nullable = false, updatable = false)
    private AiTask aiTask;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "principal_id", nullable = false, updatable = false, length = 255)
    private String principalId;

    @Column(nullable = false, updatable = false, length = 100)
    private String provider;

    @Column(name = "credential_handle", nullable = false, updatable = false, length = 255)
    private String credentialHandle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private AiCredentialBindingStatus status = AiCredentialBindingStatus.ACTIVE;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 100)
    private String revocationReason;
}
