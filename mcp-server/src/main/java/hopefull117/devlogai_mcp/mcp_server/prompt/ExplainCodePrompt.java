package hopefull117.devlogai_mcp.mcp_server.prompt;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

@Component
public class ExplainCodePrompt {
    @McpPrompt(
            name = "explain_code",
            description = "Creates a prompt for explaining source code"
    )
    public String explainCode(
            @McpArg(
                    name = "language",
                    description = "Programming language of the source code",
                    required = true
            )
            String language
    ) {
        return """
            You are reviewing %s code.
            Explain what the provided code does in a clear and concise way.
            """.formatted(language);
    }

}
