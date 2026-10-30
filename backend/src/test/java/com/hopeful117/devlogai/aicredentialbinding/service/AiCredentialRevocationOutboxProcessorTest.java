package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialRevocationOutbox;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialRevocationOutboxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCredentialRevocationOutboxProcessorTest {
    @Test
    void deletesSuccessfullyPropagatedActions() {
        var repository = mock(AiCredentialRevocationOutboxRepository.class);
        var client = mock(AiEngineCredentialAssociationClient.class);
        var action = action();
        when(repository.findTop1ByOrderByCreatedAtAsc()).thenReturn(action);
        var processor = new AiCredentialRevocationOutboxProcessor(repository, client);

        processor.process();

        verify(client).revoke("handle", "chatgpt");
        verify(repository).delete(action);
    }

    @Test
    void preservesFailedActionsForTheNextRetry() {
        var repository = mock(AiCredentialRevocationOutboxRepository.class);
        var client = mock(AiEngineCredentialAssociationClient.class);
        var action = action();
        when(repository.findTop1ByOrderByCreatedAtAsc()).thenReturn(action);
        org.mockito.Mockito.doThrow(new IllegalStateException("offline"))
                .when(client).revoke("handle", "chatgpt");
        var processor = new AiCredentialRevocationOutboxProcessor(repository, client);

        processor.process();

        verify(repository).save(action);
        org.assertj.core.api.Assertions.assertThat(action.getAttempts()).isEqualTo(1);
    }

    private static AiCredentialRevocationOutbox action() {
        var action = new AiCredentialRevocationOutbox();
        action.setCredentialHandle("handle");
        action.setProvider("chatgpt");
        return action;
    }
}
