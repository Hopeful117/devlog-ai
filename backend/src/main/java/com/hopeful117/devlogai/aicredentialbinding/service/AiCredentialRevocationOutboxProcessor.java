package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialRevocationOutbox;
import com.hopeful117.devlogai.aicredentialbinding.repository.AiCredentialRevocationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiCredentialRevocationOutboxProcessor {
    private final AiCredentialRevocationOutboxRepository repository;
    private final AiEngineCredentialAssociationClient aiEngineClient;

    @Scheduled(fixedDelayString = "${ai-credential-revocation.retry-delay-ms:30000}",
            scheduler = "aiCredentialRevocationTaskScheduler")
    @Transactional
    public void process() {
        AiCredentialRevocationOutbox action = repository.findTop1ByOrderByCreatedAtAsc();
        if (action != null) {
            try {
                aiEngineClient.revoke(action.getCredentialHandle(), action.getProvider());
                repository.delete(action);
            } catch (RuntimeException exception) {
                action.setAttempts(action.getAttempts() + 1);
                action.setLastError(exception.getClass().getSimpleName());
                repository.save(action);
            }
        }
    }
}
