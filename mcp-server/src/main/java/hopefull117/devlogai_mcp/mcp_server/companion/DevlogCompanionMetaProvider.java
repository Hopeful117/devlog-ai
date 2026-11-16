package hopefull117.devlogai_mcp.mcp_server.companion;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declares the MCP App View URI so Claude Desktop fetches and renders the
 * companion interface as an inline iframe when the tool is called.
 */
public class DevlogCompanionMetaProvider implements MetaProvider {

    @Override
    public Map<String, Object> getMeta() {
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("resourceUri", "ui://devlog-companion/app.html");
        Map<String, Object> csp = new LinkedHashMap<>();
        csp.put("connectDomains", new String[0]);
        ui.put("csp", csp);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("ui", ui);
        return meta;
    }
}
