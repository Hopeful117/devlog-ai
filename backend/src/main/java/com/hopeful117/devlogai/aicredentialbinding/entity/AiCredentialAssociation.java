package com.hopeful117.devlogai.aicredentialbinding.entity;

import jakarta.persistence.*;
import com.hopeful117.devlogai.aicredentialbinding.model.AiCredentialAssociationState;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_credential_associations",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_credential_association_principal_provider",
                columnNames = {"principal_id", "provider"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCredentialAssociation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "principal_id", nullable = false, length = 255)
    private String principalId;

    @Column(name = "chatgpt_subject", nullable = false, length = 255)
    private String chatgptSubject;

    @Column(name = "credential_handle", nullable = false, length = 255)
    private String credentialHandle;

    @Column(nullable = false, length = 100)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiCredentialAssociationState state;

    @Column(name = "granted_scopes", nullable = false, length = 1000)
    private String grantedScopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
