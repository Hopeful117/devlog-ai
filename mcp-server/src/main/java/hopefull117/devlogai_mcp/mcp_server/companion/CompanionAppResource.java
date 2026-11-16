package hopefull117.devlogai_mcp.mcp_server.companion;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves the self-contained companion HTML View bundle to Claude Desktop.
 * The bundle was built with Vite + vite-plugin-singlefile: a single HTML
 * file with all JS and CSS inlined.
 */
@Component
public class CompanionAppResource {

    private static final String HTML;

    static {
        try (InputStream is = CompanionAppResource.class.getResourceAsStream("/companion/companion-app.html")) {
            if (is == null) {
                throw new IllegalStateException("companion-app.html not found on classpath");
            }
            HTML = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load companion-app.html", e);
        }
    }

    @McpResource(
            uri = "ui://devlog-companion/app.html",
            name = "devlog-companion-app",
            description = "DevLog Companion MCP App View — inline activity feed for Claude Desktop",
            mimeType = "text/html;profile=mcp-app"
    )
    public String getCompanionApp() {
        return HTML;
    }
}
