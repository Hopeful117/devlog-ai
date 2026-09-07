package hopefull117.devlogai_mcp.mcp_server;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class StdioProtocolHygieneTest {

    private static final Duration PRE_INIT_QUIET_WINDOW = Duration.ofMillis(750);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldKeepStdoutProtocolCleanDuringStartupAndInitialization() throws Exception {
        Process process = startServerProcess();
        try (BufferedReader stdout = reader(process.getInputStream());
             BufferedReader stderr = reader(process.getErrorStream());
             Writer stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
             ExecutorService executor = Executors.newCachedThreadPool()) {

            assertThat(process.getInputStream().available())
                    .as("stdout should stay silent before the first MCP request")
                    .isZero();

            Thread.sleep(PRE_INIT_QUIET_WINDOW);

            assertThat(process.getInputStream().available())
                    .as("stdout should not emit banner, blank lines, or logs before initialize")
                    .isZero();

            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"stdio-protocol-hygiene-test","version":"1.0.0"}}}
                    """);
            JsonNode initializeResponse = readJsonLine(stdout, executor, READ_TIMEOUT);
            assertThat(initializeResponse.path("id").asInt()).isEqualTo(1);
            assertThat(initializeResponse.path("result").path("protocolVersion").asText())
                    .isEqualTo("2025-11-25");

            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                    """);
            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","id":2,"method":"resources/list","params":{}}
                    """);
            JsonNode resourcesListResponse = readJsonLine(stdout, executor, READ_TIMEOUT);
            assertThat(resourcesListResponse.path("id").asInt()).isEqualTo(2);
            assertThat(resourcesListResponse.path("result").path("resources").isArray()).isTrue();

            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","id":3,"method":"resources/templates/list","params":{}}
                    """);
            JsonNode templatesResponse = readJsonLine(stdout, executor, READ_TIMEOUT);
            assertThat(templatesResponse.path("id").asInt()).isEqualTo(3);
            assertThat(templatesResponse.path("result").path("resourceTemplates").isArray()).isTrue();

            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","id":4,"method":"tools/list","params":{}}
                    """);
            JsonNode toolsListResponse = readJsonLine(stdout, executor, READ_TIMEOUT);
            assertThat(toolsListResponse.path("id").asInt()).isEqualTo(4);
            assertThat(toolsListResponse.path("result").path("tools").isArray()).isTrue();

            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","id":5,"method":"prompts/list","params":{}}
                    """);
            JsonNode promptsListResponse = readJsonLine(stdout, executor, READ_TIMEOUT);
            assertThat(promptsListResponse.path("id").asInt()).isEqualTo(5);
            assertThat(promptsListResponse.path("result").path("prompts").isArray()).isTrue();

            writeJsonLine(stdin, """
                    {"jsonrpc":"2.0","id":6,"method":"resources/read","params":{"uri":"devlog://server/info"}}
                    """);
            JsonNode serverInfoResponse = readJsonLine(stdout, executor, READ_TIMEOUT);
            assertThat(serverInfoResponse.path("id").asInt()).isEqualTo(6);
            JsonNode serverInfo = OBJECT_MAPPER.readTree(
                    serverInfoResponse.path("result").path("contents").path(0).path("text").asText());

            assertThat(capabilityDescriptors(serverInfo.path("tools"), "name"))
                    .containsExactlyElementsOf(capabilityDescriptors(
                            toolsListResponse.path("result").path("tools"), "name"));
            assertThat(capabilityDescriptors(serverInfo.path("prompts"), "name"))
                    .containsExactlyElementsOf(capabilityDescriptors(
                            promptsListResponse.path("result").path("prompts"), "name"));

            List<String> registeredResources = new ArrayList<>(capabilityDescriptors(
                    resourcesListResponse.path("result").path("resources"), "uri"));
            registeredResources.addAll(capabilityDescriptors(
                    templatesResponse.path("result").path("resourceTemplates"), "uriTemplate"));
            registeredResources.sort(Comparator.naturalOrder());
            assertThat(capabilityDescriptors(serverInfo.path("resources"), "uri"))
                    .containsExactlyElementsOf(registeredResources);

            assertThat(capabilityValues(serverInfo.path("tools"), "name"))
                    .doesNotContain("echo_message")
                    .contains("analyze_story_context");
            assertThat(capabilityValues(serverInfo.path("prompts"), "name"))
                    .doesNotContain("explain_code");

            String stderrSnapshot = drainAvailable(stderr);
            assertThat(stderrSnapshot)
                    .as("startup diagnostics should be available on stderr")
                    .contains("Starting McpServerApplication");
            assertThat(stderrSnapshot)
                    .doesNotContain("Spring Boot ::");
        } finally {
            terminate(process);
        }
    }

    private static Process startServerProcess() throws IOException {
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                List.of(
                        "java",
                        "-cp",
                        classpath,
                        McpServerApplication.class.getName()
                )
        );
        processBuilder.directory(Path.of(".").toFile());
        processBuilder.environment().putAll(Map.of(
                "DEVLOG_BACKEND_BASE_URL", "http://localhost:18080"
        ));
        return processBuilder.start();
    }

    private static BufferedReader reader(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private static void writeJsonLine(Writer writer, String payload) throws IOException {
        writer.write(payload.strip());
        writer.write(System.lineSeparator());
        writer.flush();
    }

    private static List<String> capabilityDescriptors(JsonNode capabilities, String uriField) {
        List<String> descriptors = new ArrayList<>();
        capabilities.forEach(capability -> descriptors.add(String.join("|",
                capability.path("name").asText(),
                capability.path(uriField).asText(),
                capability.path("description").asText())));
        descriptors.sort(Comparator.naturalOrder());
        return descriptors;
    }

    private static List<String> capabilityValues(JsonNode capabilities, String field) {
        List<String> values = new ArrayList<>();
        capabilities.forEach(capability -> values.add(capability.path(field).asText()));
        return values;
    }

    private static JsonNode readJsonLine(
            BufferedReader stdout,
            ExecutorService executor,
            Duration timeout
    ) throws Exception {
        Future<String> future = executor.submit((Callable<String>) stdout::readLine);
        String line;
        try {
            line = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception wrapped) {
                throw wrapped;
            }
            throw exception;
        }

        if (line == null) {
            fail("MCP server closed stdout before replying");
        }

        try {
            return OBJECT_MAPPER.readTree(line);
        } catch (Exception parseError) {
            fail("stdout contained non-JSON protocol noise: [%s]".formatted(line), parseError);
            return null;
        }
    }

    private static String drainAvailable(BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            while (reader.ready()) {
                builder.append((char) reader.read());
            }
            if (builder.length() > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                while (reader.ready()) {
                    builder.append((char) reader.read());
                }
                break;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return builder.toString();
    }

    private static void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
