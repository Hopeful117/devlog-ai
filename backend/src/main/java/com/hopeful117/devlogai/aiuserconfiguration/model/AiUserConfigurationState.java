package com.hopeful117.devlogai.aiuserconfiguration.model;

public enum AiUserConfigurationState {
    NOT_CONFIGURED,
    CONFIGURATION_INCOMPLETE,
    READY,
    REAUTH_REQUIRED,
    PROVIDER_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    CREDENTIAL_INVALID,
    REVOKED
}
