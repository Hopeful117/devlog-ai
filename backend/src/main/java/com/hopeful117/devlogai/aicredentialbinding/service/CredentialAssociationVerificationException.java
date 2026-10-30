package com.hopeful117.devlogai.aicredentialbinding.service;

public class CredentialAssociationVerificationException extends RuntimeException {
    public CredentialAssociationVerificationException() {
        super("AI credential association could not be verified");
    }
}
