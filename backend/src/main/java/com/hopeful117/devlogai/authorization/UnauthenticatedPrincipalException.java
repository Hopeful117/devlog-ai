package com.hopeful117.devlogai.authorization;

public class UnauthenticatedPrincipalException extends RuntimeException {
    public UnauthenticatedPrincipalException() {
        super("Authentication is required");
    }
}
