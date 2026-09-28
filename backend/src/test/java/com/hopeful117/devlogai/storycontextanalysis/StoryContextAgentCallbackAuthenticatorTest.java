package com.hopeful117.devlogai.storycontextanalysis;

import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StoryContextAgentCallbackAuthenticatorTest {
    @Test
    void signsMethodPathAndExactBodyAndRejectsChanges() throws Exception {
        String method = "POST";
        String path = "/api/v1/story-context-agent/tasks/42/callback";
        byte[] body = "{\"status\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8);
        String signature = HexFormat.of().formatHex(hmac("secret", method + "\n" + path + "\n", body));
        var authenticator = new StoryContextAgentCallbackAuthenticator("secret");

        assertTrue(authenticator.isValid(method, path, body, "sha256=" + signature));
        assertFalse(authenticator.isValid("PUT", path, body, signature));
        assertFalse(authenticator.isValid(method, path + "/", body, signature));
        assertFalse(authenticator.isValid(method, path, "{}".getBytes(StandardCharsets.UTF_8), signature));
    }

    @Test
    void missingSecretFailsClosed() {
        var authenticator = new StoryContextAgentCallbackAuthenticator("");
        assertFalse(authenticator.isValid("POST", "/callback", new byte[0], "0".repeat(64)));
    }

    private byte[] hmac(String secret, String prefix, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] input = new byte[prefix.getBytes(StandardCharsets.UTF_8).length + body.length];
        System.arraycopy(prefix.getBytes(StandardCharsets.UTF_8), 0, input, 0, prefix.getBytes(StandardCharsets.UTF_8).length);
        System.arraycopy(body, 0, input, prefix.getBytes(StandardCharsets.UTF_8).length, body.length);
        return mac.doFinal(input);
    }
}
