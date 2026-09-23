package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/** Thin MCP projection; canonical identity remains the supplied reference. */
@Component
public class EvidenceResource {
    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public EvidenceResource(DevlogResourceClient resourceClient, ResourceSupport support) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://evidence/{reference}",
            name = "evidence-resolution",
            description = "Resolves a known canonical evidence reference",
            mimeType = "application/json"
    )
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw ResourceSupport.invalidParams("Evidence reference must not be blank");
        }
        return support.getScoped(
                () -> resourceClient.resolveEvidence(reference, "CURRENT", null),
                "Evidence '%s' not found".formatted(reference));
    }
}
