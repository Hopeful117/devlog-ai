package com.hopeful117.devlogai.aicredentialbinding.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Signs only the internal Core-to-AI binding request; it never handles OAuth tokens. */
public final class AiEngineBindingRequestSigner {
    public static final String SIGNATURE_HEADER = "X-DevLog-Service-Signature";
    public static final String TIMESTAMP_HEADER = "X-DevLog-Service-Timestamp";
    public static final String NONCE_HEADER = "X-DevLog-Service-Nonce";
    public static final String REQUEST_ID_HEADER = "X-DevLog-Service-Request-Id";

    private final byte[] secret;

    public AiEngineBindingRequestSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("binding service secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, String> sign(String method, String path, byte[] body,
                                    long timestamp, String nonce, String requestId) {
        String timestampValue = Long.toString(timestamp);
        byte[] payload = canonicalRequest(method, path, timestampValue, nonce, requestId, body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Map.of(
                    SIGNATURE_HEADER, "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload)),
                    TIMESTAMP_HEADER, timestampValue,
                    NONCE_HEADER, requireValue(nonce, "nonce"),
                    REQUEST_ID_HEADER, requireValue(requestId, "requestId")
            );
        } catch (Exception exception) {
            throw new IllegalStateException("unable to sign binding request", exception);
        }
    }

    static byte[] canonicalRequest(String method, String path, String timestamp,
                                   String nonce, String requestId, byte[] body) {
        String prefix = String.join("\n",
                requireValue(method, "method").toUpperCase(),
                requireValue(path, "path"),
                requireValue(timestamp, "timestamp"),
                requireValue(nonce, "nonce"),
                requireValue(requestId, "requestId"));
        byte[] prefixBytes = (prefix + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] requestBody = body == null ? new byte[0] : body;
        byte[] result = new byte[prefixBytes.length + requestBody.length];
        System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
        System.arraycopy(requestBody, 0, result, prefixBytes.length, requestBody.length);
        return result;
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
