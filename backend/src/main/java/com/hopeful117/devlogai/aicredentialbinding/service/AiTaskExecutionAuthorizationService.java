package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiUserConfigurationState;
import com.hopeful117.devlogai.aiuserconfiguration.repository.AiUserConfigurationRepository;
import com.hopeful117.devlogai.aiuserconfiguration.service.AiUserConfigurationService;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.model.AiProviderIdentity;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialAssociationRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Core-owned authorization boundary for user-bound AI task execution. */
@Service
@RequiredArgsConstructor
public class AiTaskExecutionAuthorizationService {
    private final AiUserConfigurationRepository configurationRepository;
    private final AiUserConfigurationService configurationService;
    private final AiCredentialAssociationRepository associationRepository;
    private final AiCredentialBindingService bindingService;

    @Transactional
    public AuthorizedExecution authorizeAndIssue(AuthenticatedPrincipal principal, AiTask task) {
        if (principal == null) {
            throw new IllegalStateException("authenticated principal is required for user-bound AI execution");
        }
        AiUserConfiguration configuration = configurationRepository.findByPrincipalId(principal.principalId())
                .orElseThrow(() -> rejected("AI user configuration is not ready"));
        String provider = AiProviderIdentity.engineProvider(configuration.getProvider());
        var association = associationRepository.findByPrincipalIdAndProvider(principal.principalId(), provider);
        AiUserConfigurationState state = configurationService.state(Optional.of(configuration), association);
        if (state != AiUserConfigurationState.READY) {
            throw rejected("AI user configuration is not READY: " + state);
        }
        if (!AiProviderIdentity.isChatGpt(configuration.getProvider())
                && !"anthropic".equals(configuration.getProvider())) {
            throw rejected("provider is not supported for user-bound Story Agent execution");
        }
        AiCredentialBinding binding = bindingService.issue(principal, task.getId(), provider);
        task.setProvider(provider);
        task.setModelIdentifier(configuration.getSelectedModel());
        return new AuthorizedExecution(binding, provider, configuration.getSelectedModel());
    }

    private IllegalStateException rejected(String message) {
        return new IllegalStateException(message);
    }

    public record AuthorizedExecution(AiCredentialBinding binding, String provider, String model) {
    }
}
