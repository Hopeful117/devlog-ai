package com.hopeful117.devlogai.storycontextanalysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** HMAC authentication for the dedicated SCA callback. Missing configuration rejects all requests. */
@Component
public class StoryContextAgentCallbackAuthenticator {
    private final String secret;
    private final Map<String, Long> seenRequests = new ConcurrentHashMap<>();

    public StoryContextAgentCallbackAuthenticator(
            @Value("${devlog.service-auth.secret:${sca.callback.secret:}}") String secret) {
        this.secret = secret == null ? "" : secret;
    }

    public boolean isValid(String method, String path, byte[] body, String suppliedSignature) {
        if (secret.isBlank() || suppliedSignature == null || suppliedSignature.isBlank()) return false;
        String normalized = suppliedSignature.startsWith("sha256=")
                ? suppliedSignature.substring("sha256=".length()) : suppliedSignature;
        if (!normalized.matches("[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signed = mac.doFinal(concat(expectedInput(method, path), body));
            return MessageDigest.isEqual(signed, hex(normalized));
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isServiceValid(String method, String path, byte[] body,
                                  String suppliedSignature, String timestamp,
                                  String nonce, String requestId) {
        if (secret.isBlank() || suppliedSignature == null || timestamp == null
                || nonce == null || requestId == null) return false;
        long timestampValue;
        try {
            timestampValue = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestampValue) > 60) return false;
        String replayKey = requestId + ":" + nonce;
        seenRequests.entrySet().removeIf(entry -> entry.getValue() < now - 120);
        String input = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n"
                + nonce + "\n" + requestId + "\n";
        if (!isValidHmac(input.getBytes(StandardCharsets.UTF_8), body, suppliedSignature)) return false;
        return seenRequests.putIfAbsent(replayKey, now) == null;
    }

    private boolean isValidHmac(byte[] prefix, byte[] body, String suppliedSignature) {
        String normalized = suppliedSignature.startsWith("sha256=")
                ? suppliedSignature.substring("sha256=".length()) : suppliedSignature;
        if (!normalized.matches("[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(concat(prefix, body)), hex(normalized));
        } catch (Exception ignored) {
            return false;
        }
    }

    private byte[] expectedInput(String method, String path) {
        return (method + "\n" + path + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] concat(byte[] prefix, byte[] body) {
        byte[] result = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(body, 0, result, prefix.length, body.length);
        return result;
    }

    private byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
