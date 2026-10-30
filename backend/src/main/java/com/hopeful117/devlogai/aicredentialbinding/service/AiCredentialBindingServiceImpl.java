package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBindingStatus;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialAssociationRepository;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialBindingRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.ProjectMembershipRepository;
import com.hopeful117.devlogai.authorization.ProjectRole;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCredentialBindingServiceImpl implements AiCredentialBindingService {
    private static final List<ProjectRole> ALLOWED_ROLES = List.of(
            ProjectRole.PROJECT_READER, ProjectRole.PROJECT_OWNER);

    private final AiCredentialBindingRepository bindingRepository;
    private final AiCredentialAssociationRepository associationRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final Clock clock;

    @Value("${ai-credential-binding.ttl:PT15M}")
    private Duration bindingTtl;

    @Override
    @Transactional
    public AiCredentialBinding issue(AuthenticatedPrincipal principal, UUID taskId,
                                     String provider, String credentialHandle) {
        requirePrincipal(principal);
        requireValue(provider, "provider");
        requireValue(credentialHandle, "credentialHandle");
        AiTask task = taskRepository(taskId);
        if (task.getStatus() != com.hopeful117.devlogai.ai.task.entity.AiTaskStatus.CREATED) {
            throw new IllegalStateException("AI credential binding must be issued before task submission");
        }
        if (bindingTtl == null || bindingTtl.isZero() || bindingTtl.isNegative()) {
            throw new IllegalStateException("AI credential binding TTL must be positive");
        }
        authorize(principal, task);
        Instant issuedAt = clock.instant();
        AiCredentialBinding binding = bindingRepository.save(AiCredentialBinding.builder()
                .aiTask(task)
                .project(task.getAnalysis().getProject())
                .principalId(principal.principalId())
                .provider(provider)
                .credentialHandle(credentialHandle)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(bindingTtl))
                .build());
        task.setCredentialBindingId(binding.getId());
        aiTaskRepository.save(task);
        return binding;
    }

    @Override
    @Transactional
    public AiCredentialBinding issue(AuthenticatedPrincipal principal, UUID taskId, String provider) {
        requirePrincipal(principal);
        AiCredentialAssociation association = associationRepository
                .findByPrincipalIdAndProvider(principal.principalId(), provider)
                .orElseThrow(() -> new EntityNotFoundException("AI credential association", principal.principalId()));
        return issue(principal, taskId, provider, association.getCredentialHandle());
    }

    @Override
    @Transactional
    public AiCredentialBinding validate(AuthenticatedPrincipal principal, UUID bindingId,
                                        UUID taskId, String provider) {
        requirePrincipal(principal);
        AiCredentialBinding binding = bindingRepository.findByIdForUpdate(bindingId)
                .orElseThrow(() -> notFound(bindingId));
        Instant now = clock.instant();
        if (binding.getStatus() == AiCredentialBindingStatus.ACTIVE
                && !now.isBefore(binding.getExpiresAt())) {
            binding.setStatus(AiCredentialBindingStatus.EXPIRED);
            bindingRepository.save(binding);
        }
        if (binding.getStatus() != AiCredentialBindingStatus.ACTIVE
                || !binding.getPrincipalId().equals(principal.principalId())
                || !binding.getAiTask().getId().equals(taskId)
                || !binding.getProvider().equals(provider)) {
            throw notFound(bindingId);
        }
        authorize(principal, binding.getAiTask());
        return binding;
    }

    @Override
    @Transactional
    public void revoke(AuthenticatedPrincipal principal, UUID bindingId, String reason) {
        requirePrincipal(principal);
        AiCredentialBinding binding = bindingRepository.findByIdForUpdate(bindingId)
                .orElseThrow(() -> notFound(bindingId));
        authorize(principal, binding.getAiTask());
        if (binding.getStatus() == AiCredentialBindingStatus.ACTIVE) {
            binding.setStatus(AiCredentialBindingStatus.REVOKED);
            binding.setRevokedAt(clock.instant());
            binding.setRevocationReason(reason == null || reason.isBlank()
                    ? "REVOKED_BY_PRINCIPAL" : reason.substring(0, Math.min(reason.length(), 100)));
            bindingRepository.save(binding);
        }
    }

    private AiTask taskRepository(UUID taskId) {
        return aiTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("AI task", taskId));
    }

    private void authorize(AuthenticatedPrincipal principal, AiTask task) {
        if (task.getAnalysis() == null || task.getAnalysis().getProject() == null
                || !membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                principal.principalId(), task.getAnalysis().getProject().getId(), ALLOWED_ROLES)) {
            throw new EntityNotFoundException("AI credential binding", task.getId());
        }
    }

    private void requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authenticated principal is required");
    }

    private void requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private EntityNotFoundException notFound(UUID bindingId) {
        return new EntityNotFoundException("AI credential binding", bindingId);
    }
}
