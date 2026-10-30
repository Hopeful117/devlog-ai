package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;

import java.util.UUID;

public interface AiCredentialBindingService {
    AiCredentialBinding issue(AuthenticatedPrincipal principal, UUID taskId,
                              String provider, String credentialHandle);

    AiCredentialBinding issue(AuthenticatedPrincipal principal, UUID taskId, String provider);

    AiCredentialBinding validate(AuthenticatedPrincipal principal, UUID bindingId,
                                 UUID taskId, String provider);

    void revoke(AuthenticatedPrincipal principal, UUID bindingId, String reason);
}
