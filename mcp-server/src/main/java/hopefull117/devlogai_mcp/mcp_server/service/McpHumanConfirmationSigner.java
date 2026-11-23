package hopefull117.devlogai_mcp.mcp_server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class McpHumanConfirmationSigner {
    private final String secret;

    public McpHumanConfirmationSigner(
            @Value("${devlog.mcp.human-confirmation.secret:${devlog.service-auth.secret:}}") String secret) {
        this.secret = secret;
    }

    public String sign(UUID authorizationId, String planDigest) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("MCP human confirmation secret is not configured");
        }
        long timestamp = Instant.now(Clock.systemUTC()).getEpochSecond();
        String payload = authorizationId + "\n" + planDigest + "\n" + timestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return timestamp + "." + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
