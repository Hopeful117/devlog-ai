package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.test-structure",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class TestStructureCollector extends AbstractFileCollector {
    private static final String VERSION = "test-structure-v1";
    private static final String REPOSITORY_ROOT = "repository:/";
    private static final Map<String, String> FRAMEWORKS = new LinkedHashMap<>();
    static {
        FRAMEWORKS.put("junit", "JUnit");
        FRAMEWORKS.put("testng", "TestNG");
        FRAMEWORKS.put("pytest", "pytest");
        FRAMEWORKS.put("vitest", "Vitest");
        FRAMEWORKS.put("jest", "Jest");
    }

    public TestStructureCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        super(scanner, limits);
    }
    @Override public CollectorType type() { return CollectorType.TEST_STRUCTURE; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        RepositoryScan scan = scan(context, this::descriptor);
        FactAccumulator facts = accumulator(context, scan);
        TestCounts counts = new TestCounts();
        for (RepositoryFile file : scan.files()) {
            counts.include(file.relativePath().toLowerCase(Locale.ROOT));
            if (file.content() != null) detectFrameworks(file, facts);
        }
        if (counts.sourceDirectory) facts.add(FactType.TEST_SOURCE_DIRECTORY_PRESENT,
                "testSourceDirectoryPresent=true", REPOSITORY_ROOT);
        if (counts.resourceDirectory) facts.add(FactType.TEST_RESOURCE_DIRECTORY_PRESENT,
                "testResourceDirectoryPresent=true", REPOSITORY_ROOT);
        if (counts.unit > 0) facts.add(FactType.TEST_FILE_PRESENT,
                "category=UNIT%nfileCount=" + counts.unit, REPOSITORY_ROOT);
        if (counts.integration > 0) facts.add(FactType.INTEGRATION_TEST_FILE_PRESENT,
                "category=INTEGRATION%nfileCount=" + counts.integration, REPOSITORY_ROOT);
        return result(facts);
    }

    private boolean descriptor(String path) {
        return named(path, "pom.xml", "build.gradle", "build.gradle.kts", "pyproject.toml",
                "package.json", "requirements.txt");
    }

    private void detectFrameworks(RepositoryFile file, FactAccumulator facts) {
        String lower = file.content().toLowerCase(Locale.ROOT);
        FRAMEWORKS.forEach((token, framework) -> {
            if (lower.contains(token)) facts.add(FactType.TEST_FRAMEWORK_DECLARED,
                    "framework=" + framework, file.relativePath());
        });
        if (lower.contains("testcontainers")) facts.add(FactType.TESTCONTAINERS_DECLARED,
                "library=Testcontainers", file.relativePath());
    }

    private final class TestCounts {
        private int unit;
        private int integration;
        private boolean sourceDirectory;
        private boolean resourceDirectory;

        private void include(String path) {
            if (isTestPath(path)) sourceDirectory = true;
            if (path.contains("src/test/resources/") || path.startsWith("tests/resources/")) {
                resourceDirectory = true;
            }
            if (isTestFile(path)) {
                if (isIntegrationTest(path)) integration++;
                else unit++;
            }
        }

        private boolean isTestFile(String path) {
            String name = fileName(path);
            return isTestPath(path) && (name.matches(
                    ".*(?:test|tests|spec)\\.(?:java|kt|py|js|jsx|ts|tsx)")
                    || name.matches("(?:test_.*|.*_test)\\.py"));
        }

        private boolean isTestPath(String path) {
            return path.contains("src/test/") || path.startsWith("tests/")
                    || path.startsWith("test/") || path.contains("/__tests__/");
        }

        private boolean isIntegrationTest(String path) {
            String name = fileName(path);
            return path.contains("integration") || path.contains("e2e")
                    || name.matches(".*(?:it|integrationtest|e2e)\\.[^.]+$");
        }
    }
}
