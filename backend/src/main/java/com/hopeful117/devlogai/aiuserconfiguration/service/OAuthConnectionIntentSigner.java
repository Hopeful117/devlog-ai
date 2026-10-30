package com.hopeful117.devlogai.aiuserconfiguration.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class OAuthConnectionIntentSigner {
    private OAuthConnectionIntentSigner() {
    }

    public static String sign(String secret, UUID intentId, String provider, long expiresAt,
                              String principalBinding) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireSecret(secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(intentId, provider, expiresAt, principalBinding)));
        } catch (Exception exception) {
            throw new IllegalStateException("unable to sign OAuth connection intent", exception);
        }
    }

    public static boolean isValid(String secret, UUID intentId, String provider, long expiresAt,
                                  String principalId, String signature) {
        if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
        try {
            byte[] expected = HexFormat.of().parseHex(sign(secret, intentId, provider, expiresAt, principalId));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static String principalBinding(String secret, String principalId) {
        if (secret == null || secret.isBlank() || principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principal id is incomplete");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(principalId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("unable to derive principal binding", exception);
        }
    }

    private static byte[] canonical(UUID intentId, String provider, long expiresAt, String principalId) {
        if (intentId == null || provider == null || provider.isBlank()
                || principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("OAuth connection intent data is incomplete");
        }
        return (intentId + "\n" + provider + "\n" + expiresAt + "\n" + principalId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("service secret is not configured");
        return secret;
    }
}
