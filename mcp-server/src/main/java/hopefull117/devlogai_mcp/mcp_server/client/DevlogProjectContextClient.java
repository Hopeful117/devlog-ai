package hopefull117.devlogai_mcp.mcp_server.client;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;



@HttpExchange("/api/v1/projects")
public interface DevlogProjectContextClient {
    @GetExchange("/{projectSlug}/context")
    ProjectContext getProjectContext(@PathVariable String projectSlug);

    @GetExchange("/{projectSlug}/engineering-context")
    EngineeringContext getEngineeringContext(@PathVariable String projectSlug,@RequestParam("intent") String intent);
}