package com.hopeful117.devlogai.storycontextanalysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** HMAC authentication for the dedicated SCA callback. Missing configuration rejects all requests. */
@Component
public class StoryContextAgentCallbackAuthenticator {
    private final String secret;

    public StoryContextAgentCallbackAuthenticator(
            @Value("${sca.callback.secret:}") String secret) {
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
