package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DockerCollectorWiringTest {

    @TempDir
    Path tempDir;

    private CollectionContext createContext(Path path) {
        return new CollectionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                path, "abc123", SourceType.GIT_REPOSITORY, Instant.now());
    }

    private CollectorLimits createLimits() {
        CollectorLimits limits = new CollectorLimits();
        limits.setCollectorTimeout(java.time.Duration.ofSeconds(30));
        limits.setMaxTotalBytes(10_000_000);
        return limits;
    }

    private SecureRepositoryScanner createScanner() {
        return new SecureRepositoryScanner(createLimits());
    }

    private DockerCollector createCollector() {
        return new DockerCollector(createScanner(), createLimits());
    }

    @Test
    void shouldDetectDependsOnRelationship() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    depends_on:
                      - postgres
                  postgres:
                    image: postgres:17
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_SERVICE_DECLARED
                        && f.content().contains("service=backend")));
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_SERVICE_DECLARED
                        && f.content().contains("service=postgres")));
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_SERVICE_DEPENDS_ON
                        && f.content().equals("from=backend,to=postgres")));
    }

    @Test
    void shouldDetectEnvVarServiceReference() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      AI_ENGINE_BASE_URL: http://ai-engine:8000
                  ai-engine:
                    image: ai-engine:latest
                    environment:
                      CORE_BASE_URL: http://backend:8080
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        List<String> envRefs = result.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .toList();
        assertFalse(envRefs.isEmpty(), "Should detect env var service references");
        assertTrue(envRefs.stream().anyMatch(r -> r.contains("from=backend") && r.contains("to=ai-engine")),
                "Should detect backend→ai-engine reference");
        assertTrue(envRefs.stream().anyMatch(r -> r.contains("from=ai-engine") && r.contains("to=backend")),
                "Should detect ai-engine→backend reference");
    }

    @Test
    void shouldNotCreateSelfReference() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      SERVER_PORT: "8080"
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        List<String> envRefs = result.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .toList();
        assertTrue(envRefs.stream().noneMatch(r -> r.contains("from=backend") && r.contains("to=backend")),
                "Should not create self-references");
    }

    @Test
    void shouldNotTreatNonServiceEnvVarsAsReferences() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      SERVER_PORT: "8080"
                      JAVA_OPTS: "-Xmx512m"
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        List<String> envRefs = result.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .toList();
        assertTrue(envRefs.isEmpty(), "Non-service env vars should not produce references");
    }

    @Test
    void shouldNotInferServiceFromUnresolvedVariableName() throws Exception {
        String compose = """
                services:
                  backend:
                    environment:
                      DB_HOST: ${POSTGRES_HOST}
                  postgres:
                    image: postgres:17
                """;
        Files.writeString(tempDir.resolve("compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .noneMatch(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE));
    }

    @Test
    void shouldDetectLiteralJdbcAndVariableDefaultServiceHosts() throws Exception {
        String compose = """
                services:
                  backend:
                    environment:
                      DB_HOST: postgres
                      DB_URL: jdbc:postgresql://postgres:5432/app
                      CACHE_HOST: ${CACHE_HOST:-cache}
                  postgres:
                    image: postgres:17
                  cache:
                    image: redis:7
                """;
        Files.writeString(tempDir.resolve("compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));
        List<String> relationships = result.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .toList();

        assertTrue(relationships.stream().anyMatch(value -> value.contains("to=postgres")));
        assertTrue(relationships.stream().anyMatch(value -> value.contains("to=cache")));
    }

    @Test
    void shouldHandleQuotedServicesAlternateIndentAndInlineDependsOnMap() throws Exception {
        String compose = """
                services:
                    "api":
                        depends_on: {"database": {condition: service_healthy}}
                    "database":
                        image: postgres:17
                """;
        Files.writeString(tempDir.resolve("compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        assertTrue(result.facts().stream().anyMatch(f ->
                f.type() == FactType.DOCKER_SERVICE_DEPENDS_ON
                        && f.content().equals("from=api,to=database")));
    }

    @Test
    void shouldPreserveProvenanceInEnvReference() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      AI_ENGINE_URL: http://ai-engine:8000
                  ai-engine:
                    image: ai-engine:latest
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        List<CollectedFact> relationships = result.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .toList();
        assertFalse(relationships.isEmpty());
        relationships.forEach(f -> {
                    assertTrue(f.evidenceReferences().contains("docker-compose.yml"),
                            "Env reference must have provenance to source file");
                    assertTrue(f.content().contains("from="), "Content must include source service");
                    assertTrue(f.content().contains("to="), "Content must include target service");
                });
    }

    @Test
    void shouldDetectHttpServiceUrl() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      AI_ENGINE_URL: http://ai-engine:8000
                  ai-engine:
                    image: ai-engine:latest
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE
                        && f.content().contains("to=ai-engine")),
                "Should detect HTTP URL service reference");
    }

    @Test
    void shouldNotTreatLocalhostAsServiceReference() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      DB_URL: jdbc:postgresql://localhost:5432/devlog
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        List<String> envRefs = result.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .toList();
        assertTrue(envRefs.stream().noneMatch(r -> r.contains("localhost")),
                "Localhost should not be treated as a service reference");
    }

    @Test
    void shouldNotTreatExternalHttpHostAsServiceReference() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      EXTERNAL_API_URL: https://api.example.com/v1
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .noneMatch(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE),
                "External HTTP hosts should not be treated as Compose service references");
    }

    @Test
    void shouldBeDeterministic() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    environment:
                      AI_ENGINE_URL: http://ai-engine:8000
                  ai-engine:
                    image: ai-engine:latest
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult first = createCollector().collect(createContext(tempDir));
        CollectionResult second = createCollector().collect(createContext(tempDir));

        List<String> firstRefs = first.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .sorted()
                .toList();
        List<String> secondRefs = second.facts().stream()
                .filter(f -> f.type() == FactType.DOCKER_SERVICE_ENV_REFERENCE)
                .map(f -> f.content())
                .sorted()
                .toList();
        assertEquals(firstRefs, secondRefs, "Extraction must be deterministic");
    }

    @Test
    void shouldPreserveExistingComposeBehavior() throws Exception {
        String compose = """
                services:
                  backend:
                    image: backend:latest
                    healthcheck:
                      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
                  frontend:
                    image: frontend:latest
                volumes:
                  devlog-postgres-data:
                """;
        Files.writeString(tempDir.resolve("docker-compose.yml"), compose, StandardCharsets.UTF_8);

        CollectionResult result = createCollector().collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_COMPOSE_PRESENT));
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_SERVICE_DECLARED
                        && f.content().contains("service=backend")));
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_SERVICE_DECLARED
                        && f.content().contains("service=frontend")));
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_VOLUME_DECLARED
                        && f.content().contains("volume=devlog-postgres-data")));
        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == FactType.DOCKER_HEALTHCHECK_DECLARED));
    }
}
