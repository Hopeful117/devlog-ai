package com.hopeful117.devlogai.aicredentialbinding.service;

import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialAssociation;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;

public interface AiCredentialAssociationService {
    AiCredentialAssociation associate(AuthenticatedPrincipal principal, String chatgptSubject,
                                      String credentialHandle, String provider);

    AiCredentialAssociation associate(AuthenticatedPrincipal principal, String chatgptSubject,
                                      String credentialHandle, String provider,
                                      AiAuthenticationMethod authenticationMethod);

    AiCredentialAssociation require(AuthenticatedPrincipal principal, String provider);

    void revoke(AuthenticatedPrincipal principal, String provider);

    void revokeByCredentialHandle(String credentialHandle, String provider);

    void scheduleRevocation(String credentialHandle, String provider);
}
