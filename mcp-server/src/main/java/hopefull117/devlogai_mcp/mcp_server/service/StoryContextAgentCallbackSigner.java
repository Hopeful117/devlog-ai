package hopefull117.devlogai_mcp.mcp_server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Creates the exact signature expected by Core for MCP-originated callbacks. */
@Component
public class StoryContextAgentCallbackSigner {
    private final ObjectMapper objectMapper;
    private final String secret;

    public StoryContextAgentCallbackSigner(
            ObjectMapper objectMapper,
            @Value("${devlog.sca.callback.secret:}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret == null ? "" : secret;
    }

    public String sign(UUID aiTaskId, Map<String, Object> request) {
        if (secret.isBlank()) {
            throw new IllegalStateException("SCA callback signing secret is not configured");
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(request);
            String path = "/api/v1/story-context-agent/tasks/" + aiTaskId + "/callback";
            byte[] prefix = ("POST\n" + path + "\n").getBytes(StandardCharsets.UTF_8);
            byte[] input = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, input, 0, prefix.length);
            System.arraycopy(body, 0, input, prefix.length, body.length);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(input));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign SCA callback", exception);
        }
    }
}
