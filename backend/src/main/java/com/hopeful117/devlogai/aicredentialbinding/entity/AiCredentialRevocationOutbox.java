package com.hopeful117.devlogai.aicredentialbinding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_credential_revocation_outbox",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_ai_credential_revocation_outbox_handle_provider",
                columnNames = {"credential_handle", "provider"}))
@Getter
@Setter
@NoArgsConstructor
public class AiCredentialRevocationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "credential_handle", nullable = false, length = 255)
    private String credentialHandle;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
