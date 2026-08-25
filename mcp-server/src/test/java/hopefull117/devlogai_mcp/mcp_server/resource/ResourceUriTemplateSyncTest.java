package hopefull117.devlogai_mcp.mcp_server.resource;

import com.hopeful117.devlogai.contracts.engineeringcontext.DevlogResourceUriFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpResource;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard: the @McpResource URI templates declared by the mcp-server must
 * stay identical to the URIs built by DevlogResourceUriFactory, the single
 * source of truth used by the backend contract mapping layer.
 */
class ResourceUriTemplateSyncTest {

    private static final String SLUG = "sample-project";
    private static final UUID ID = UUID.fromString(
            "11111111-2222-3333-4444-555555555555");
    private static final String SHA =
            "3cd3723206eae38d518eb696a1dd50c0476264d0";

    @Test
    void decisionTemplateMatchesFactory() {
        assertThat(template(DecisionResource.class, "getDecision"))
                .isEqualTo(DevlogResourceUriFactory.decision(SLUG, ID)
                        .replace(ID.toString(), "{decisionId}")
                        .replace(SLUG, "{projectSlug}"));
    }

    @Test
    void insightTemplateMatchesFactory() {
        assertThat(template(InsightResource.class, "getInsight"))
                .isEqualTo(DevlogResourceUriFactory.insight(SLUG, ID)
                        .replace(ID.toString(), "{insightId}")
                        .replace(SLUG, "{projectSlug}"));
    }

    @Test
    void storyTemplateMatchesFactory() {
        assertThat(template(EngineeringStoryResource.class, "getStory"))
                .isEqualTo(DevlogResourceUriFactory.story(SLUG, ID)
                        .replace(ID.toString(), "{storyId}")
                        .replace(SLUG, "{projectSlug}"));
    }

    @Test
    void engineeringEventTemplateMatchesFactory() {
        assertThat(template(EngineeringEventResource.class, "getEngineeringEvent"))
                .isEqualTo(DevlogResourceUriFactory.engineeringEvent(SLUG, ID)
                        .replace(ID.toString(), "{eventId}")
                        .replace(SLUG, "{projectSlug}"));
    }

    @Test
    void commitContextTemplateMatchesFactory() {
        assertThat(template(CommitContextResource.class, "getCommitContext"))
                .isEqualTo(DevlogResourceUriFactory.commit(SLUG, SHA)
                        .replace(SHA, "{commitSha}")
                        .replace(SLUG, "{projectSlug}"));
    }

    @Test
    void projectsResourceMatchesFactory() {
        assertThat(annotation(ProjectsResource.class, "listProjects").uri())
                .isEqualTo(DevlogResourceUriFactory.projects());
    }

    @Test
    void freshnessTemplateMatchesFactory() {
        assertThat(template(FreshnessResource.class, "getFreshness"))
                .isEqualTo(DevlogResourceUriFactory.freshness(SLUG)
                        .replace(SLUG, "{projectSlug}"));
    }

    private String template(Class<?> resourceClass, String methodName) {
        return annotation(resourceClass, methodName).uri();
    }

    private McpResource annotation(Class<?> resourceClass, String methodName) {
        for (Method method : resourceClass.getDeclaredMethods()) {
            McpResource annotation = method.getAnnotation(McpResource.class);
            if (annotation != null && method.getName().equals(methodName)) {
                return annotation;
            }
        }
        throw new IllegalStateException("No @McpResource named " + methodName
                + " on " + resourceClass.getSimpleName());
    }
}
