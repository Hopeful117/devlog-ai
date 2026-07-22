package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialCollectorsTest {

    @TempDir Path workspace;
    private CollectorLimits limits;
    private SecureRepositoryScanner scanner;
    private CollectionContext context;

    @BeforeEach
    void setUp() {
        limits = new CollectorLimits();
        scanner = new SecureRepositoryScanner(limits);
        context = new CollectionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), workspace,
                "abc123", SourceType.GIT_REPOSITORY, Instant.parse("2026-07-22T10:00:00Z"));
    }

    @Test
    void repositoryMetadataShouldBeBoundedTraceableAndDeterministic() throws IOException {
        write("src/main/java/App.java", "class App {}");
        write("pom.xml", "<project/>");
        RepositoryMetadataCollector collector = new RepositoryMetadataCollector(scanner, limits);

        CollectionResult first = collector.collect(context);
        CollectionResult second = collector.collect(context);

        assertEquals(first.facts(), second.facts());
        assertTrue(has(first, FactType.REPOSITORY_REVISION_RESOLVED));
        assertTrue(has(first, FactType.SOURCE_DIRECTORY_PRESENT));
        assertTrue(first.facts().stream().allMatch(fact -> fact.source().equals("repository-metadata-v1")));
        assertTrue(first.facts().stream().flatMap(fact -> fact.evidenceReferences().stream())
                .noneMatch(reference -> reference.startsWith(workspace.toString())));
    }

    @Test
    void buildCollectorShouldParseMavenAndStaticGradleWithoutExecution() throws IOException {
        write("pom.xml", """
                <project><version>1.2.3</version><properties><java.version>21</java.version></properties>
                <dependencies><dependency><groupId>org.example</groupId><artifactId>core</artifactId></dependency></dependencies>
                <modules><module>backend</module></modules></project>
                """);
        write("frontend/build.gradle.kts", """
                version = "2.0"
                dependencies { implementation("org.sample:library:1.0") }
                java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
                """);
        BuildCollector collector = new BuildCollector(scanner, limits);

        CollectionResult result = collector.collect(context);

        assertTrue(has(result, FactType.BUILD_SYSTEM_DETECTED));
        assertTrue(has(result, FactType.DEPENDENCY_DECLARED));
        assertTrue(has(result, FactType.JAVA_VERSION_DECLARED));
        assertTrue(has(result, FactType.BUILD_MODULE_DECLARED));
    }

    @Test
    void springCollectorShouldReportDeclarationsWithoutRuntimeClaims() throws IOException {
        write("pom.xml", "<artifactId>spring-boot-starter-security</artifactId>");
        write("src/main/java/ApiController.java", "@RestController class ApiController {}");
        write("src/main/resources/application.yml", "spring:\n  application:\n    name: sample");
        SpringCollector collector = new SpringCollector(scanner, limits);

        CollectionResult result = collector.collect(context);

        assertTrue(has(result, FactType.SPRING_BOOT_DETECTED));
        assertTrue(has(result, FactType.SPRING_SECURITY_DETECTED));
        assertTrue(has(result, FactType.REST_CONTROLLER_DECLARED));
        assertTrue(has(result, FactType.SPRING_CONFIGURATION_FILE_PRESENT));
        assertFalse(result.facts().stream().anyMatch(fact -> fact.content().contains("secure")));
    }

    @Test
    void dockerCollectorShouldParseDescriptorsWithoutRunningDocker() throws IOException {
        write("Dockerfile", "FROM eclipse-temurin:21 AS build\nFROM eclipse-temurin:21\nUSER app\nHEALTHCHECK CMD true");
        write("compose.yml", "services:\n  backend:\n    image: app\nvolumes:\n  data:\n");
        write(".dockerignore", "target\n");
        DockerCollector collector = new DockerCollector(scanner, limits);

        CollectionResult result = collector.collect(context);

        assertTrue(has(result, FactType.DOCKERFILE_PRESENT));
        assertTrue(has(result, FactType.DOCKER_MULTI_STAGE_BUILD_PRESENT));
        assertTrue(has(result, FactType.DOCKER_SERVICE_DECLARED));
        assertTrue(has(result, FactType.DOCKER_VOLUME_DECLARED));
        assertTrue(has(result, FactType.DOCKERIGNORE_PRESENT));
    }

    @Test
    void documentationCollectorShouldInventoryMetadataWithoutJudgingQuality() throws IOException {
        write("README.md", "# Sample\nDocumentation");
        write("docs/decisions/ADR-001.md", "# ADR-001\nDecision");
        DocumentationCollector collector = new DocumentationCollector(scanner, limits);

        CollectionResult result = collector.collect(context);

        assertTrue(has(result, FactType.README_PRESENT));
        assertTrue(has(result, FactType.ADR_DOCUMENT_PRESENT));
        assertTrue(has(result, FactType.DOCUMENTATION_DIRECTORY_PRESENT));
        assertTrue(result.facts().stream().noneMatch(fact -> fact.content().contains("quality")));
    }

    @Test
    void testCollectorShouldSummarizeStructureWithoutExecutingTests() throws IOException {
        write("pom.xml", "<artifactId>junit-jupiter</artifactId><artifactId>testcontainers</artifactId>");
        write("src/test/java/UnitTest.java", "class UnitTest {}");
        write("src/test/java/UserIntegrationTest.java", "class UserIntegrationTest {}");
        write("src/test/resources/application-test.yml", "spring: {}");
        TestStructureCollector collector = new TestStructureCollector(scanner, limits);

        CollectionResult result = collector.collect(context);

        assertTrue(has(result, FactType.TEST_SOURCE_DIRECTORY_PRESENT));
        assertTrue(has(result, FactType.TEST_FILE_PRESENT));
        assertTrue(has(result, FactType.INTEGRATION_TEST_FILE_PRESENT));
        assertTrue(has(result, FactType.TEST_FRAMEWORK_DECLARED));
        assertTrue(has(result, FactType.TESTCONTAINERS_DECLARED));
        assertTrue(has(result, FactType.TEST_RESOURCE_DIRECTORY_PRESENT));
    }

    @Test
    void scannerShouldSkipSymlinksExcludedDirectoriesAndReportLimits() throws IOException {
        limits.setMaxFiles(1);
        write("target/generated.java", "generated");
        write("safe.txt", "safe");
        write("second.txt", "second");
        Path external = Files.createTempFile("outside-workspace", ".txt");
        Files.createSymbolicLink(workspace.resolve("external-link"), external);

        RepositoryScan scan = scanner.scan(context, ignored -> true);

        assertTrue(scan.warnings().stream().anyMatch(warning -> warning.code().equals("MAX_FILES_REACHED")));
        assertTrue(scan.files().stream().noneMatch(file -> file.relativePath().contains("target")));
        assertTrue(scan.files().stream().noneMatch(file -> file.relativePath().equals("external-link")));
    }

    @Test
    void fingerprintShouldIncludeCollectorVersionRevisionAndNormalizedEvidence() {
        CollectedFact first = CollectedFact.create("build-v1", FactType.BUILD_SYSTEM_DETECTED,
                "buildSystem=MAVEN", java.util.List.of("pom.xml", "pom.xml"), "abc");
        CollectedFact same = CollectedFact.create("build-v1", FactType.BUILD_SYSTEM_DETECTED,
                "buildSystem=MAVEN\n", java.util.List.of("pom.xml"), "abc");
        CollectedFact differentRevision = CollectedFact.create("build-v1", FactType.BUILD_SYSTEM_DETECTED,
                "buildSystem=MAVEN", java.util.List.of("pom.xml"), "def");

        assertEquals(first.fingerprint(), same.fingerprint());
        assertFalse(first.fingerprint().equals(differentRevision.fingerprint()));
        assertEquals(64, first.fingerprint().length());
    }

    private boolean has(CollectionResult result, FactType type) {
        return result.facts().stream().anyMatch(fact -> fact.type() == type);
    }

    private void write(String relative, String content) throws IOException {
        Path file = workspace.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
