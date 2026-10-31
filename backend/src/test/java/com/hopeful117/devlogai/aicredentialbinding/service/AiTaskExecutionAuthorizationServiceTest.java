package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.aiuserconfiguration.entity.AiUserConfiguration;
import com.hopeful117.devlogai.aiuserconfiguration.model.AiUserConfigurationState;
import com.hopeful117.devlogai.aiuserconfiguration.repository.AiUserConfigurationRepository;
import com.hopeful117.devlogai.aiuserconfiguration.service.AiUserConfigurationService;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialAssociationRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTaskExecutionAuthorizationServiceTest {
    @Mock AiUserConfigurationRepository configurations;
    @Mock AiUserConfigurationService configurationService;
    @Mock AiCredentialAssociationRepository associations;
    @Mock AiCredentialBindingService bindings;

    @Test
    void readyChatGptConfigurationIssuesTaskScopedExecution() {
        AiUserConfiguration configuration = configuration("openai_chatgpt", "gpt-5.6-luna");
        AiTask task = mock(AiTask.class);
        UUID taskId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        when(task.getId()).thenReturn(taskId);
        when(configurations.findByPrincipalId("principal-1")).thenReturn(Optional.of(configuration));
        when(associations.findByPrincipalIdAndProvider("principal-1", "chatgpt"))
                .thenReturn(Optional.of(new AiCredentialAssociation()));
        when(configurationService.state(any(), any())).thenReturn(AiUserConfigurationState.READY);
        AiCredentialBinding binding = AiCredentialBinding.builder().id(bindingId).build();
        when(bindings.issue(any(), eq(taskId), eq("chatgpt"))).thenReturn(binding);

        var result = service().authorizeAndIssue(principal(), task);

        assertEquals(bindingId, result.binding().getId());
        assertEquals("chatgpt", result.provider());
        assertEquals("gpt-5.6-luna", result.model());
        verify(task).setProvider("chatgpt");
        verify(task).setModelIdentifier("gpt-5.6-luna");
    }

    @Test
    void readyAnthropicConfigurationUsesAnthropicBinding() {
        AiUserConfiguration configuration = mock(AiUserConfiguration.class);
        when(configuration.getProvider()).thenReturn("anthropic");
        when(configuration.getSelectedModel()).thenReturn("claude-opus-5-5");
        AiTask task = mock(AiTask.class);
        UUID taskId = UUID.randomUUID();
        when(task.getId()).thenReturn(taskId);
        when(configurations.findByPrincipalId("principal-1")).thenReturn(Optional.of(configuration));
        when(associations.findByPrincipalIdAndProvider("principal-1", "anthropic"))
                .thenReturn(Optional.of(new AiCredentialAssociation()));
        when(configurationService.state(any(), any())).thenReturn(AiUserConfigurationState.READY);
        when(bindings.issue(any(), eq(taskId), eq("anthropic")))
                .thenReturn(AiCredentialBinding.builder().id(UUID.randomUUID()).build());

        assertEquals("anthropic", service().authorizeAndIssue(principal(), task).provider());
        verify(bindings).issue(any(), eq(taskId), eq("anthropic"));
    }

    @Test
    void nonReadyConfigurationFailsClosedBeforeIssuingBinding() {
        AiUserConfiguration configuration = mock(AiUserConfiguration.class);
        when(configuration.getProvider()).thenReturn("anthropic");
        AiTask task = mock(AiTask.class);
        when(configurations.findByPrincipalId("principal-1")).thenReturn(Optional.of(configuration));
        when(associations.findByPrincipalIdAndProvider("principal-1", "anthropic"))
                .thenReturn(Optional.empty());
        when(configurationService.state(any(), any())).thenReturn(AiUserConfigurationState.REAUTH_REQUIRED);

        assertThrows(IllegalStateException.class, () -> service().authorizeAndIssue(principal(), task));
        verifyNoInteractions(bindings);
    }

    private AiTaskExecutionAuthorizationService service() {
        return new AiTaskExecutionAuthorizationService(configurations, configurationService, associations, bindings);
    }

    private static AiUserConfiguration configuration(String provider, String model) {
        AiUserConfiguration value = mock(AiUserConfiguration.class);
        when(value.getProvider()).thenReturn(provider);
        when(value.getSelectedModel()).thenReturn(model);
        return value;
    }

    private static AuthenticatedPrincipal principal() {
        return new AuthenticatedPrincipal("principal-1", PrincipalKind.HUMAN, "issuer");
    }
}
