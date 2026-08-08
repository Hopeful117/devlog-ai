package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BuildCollectorAdditionalTest {

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

    @Test
    void shouldDetectMalformedMavenDescriptor() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<project><groupId>com.example</groupId>", StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .anyMatch(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_SYSTEM_DETECTED
                        && f.content().contains("MAVEN")));
        assertTrue(result.warnings().stream()
                .anyMatch(w -> w.code().equals("MALFORMED_MAVEN_DESCRIPTOR")));
    }

    @Test
    void shouldParseMavenDependencies() throws Exception {
        String pom = """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.google.guava</groupId>
                            <artifactId>guava</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.DEPENDENCY_DECLARED)
                .anyMatch(f -> f.content().contains("spring-core")));
        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.DEPENDENCY_DECLARED)
                .anyMatch(f -> f.content().contains("guava")));
    }

    @Test
    void shouldParseMavenPlugins() throws Exception {
        String pom = """
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                            </plugin>
                            <plugin>
                                <artifactId>maven-surefire-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_PLUGIN_DECLARED)
                .anyMatch(f -> f.content().contains("maven-compiler-plugin")));
    }

    @Test
    void shouldParseMavenModules() throws Exception {
        String pom = """
                <project>
                    <modules>
                        <module>core</module>
                        <module>api</module>
                    </modules>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_MODULE_DECLARED)
                .anyMatch(f -> f.content().contains("core")));
    }

    @Test
    void shouldParseMavenJavaVersion() throws Exception {
        String pom = """
                <project>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                    </properties>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.JAVA_VERSION_DECLARED)
                .anyMatch(f -> f.content().contains("21")));
    }

    @Test
    void shouldParseMavenProjectVersion() throws Exception {
        String pom = """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>my-app</artifactId>
                    <version>3.2.1</version>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.PROJECT_VERSION_DECLARED)
                .anyMatch(f -> f.content().contains("3.2.1")));
    }

    @Test
    void shouldDetectGradleDependencies() throws Exception {
        String gradle = """
                dependencies {
                    implementation 'org.springframework:spring-core:6.1.0'
                    testImplementation 'junit:junit:4.13.2'
                }
                """;
        Files.writeString(tempDir.resolve("build.gradle"), gradle, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_SYSTEM_DETECTED)
                .anyMatch(f -> f.content().contains("GRADLE")));
        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.DEPENDENCY_DECLARED)
                .anyMatch(f -> f.content().contains("spring-core")));
    }

    @Test
    void shouldWarnOnUnsupportedGradleDeclaration() throws Exception {
        String gradle = """
                dependencies {
                    implementation someDynamicDep()
                }
                """;
        Files.writeString(tempDir.resolve("build.gradle"), gradle, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.warnings().stream()
                .anyMatch(w -> w.code().equals("UNSUPPORTED_GRADLE_DECLARATION")));
    }

    @Test
    void shouldParseGradleJavaVersion() throws Exception {
        String gradle = """
                sourceCompatibility = JavaVersion.VERSION_21
                """;
        Files.writeString(tempDir.resolve("build.gradle"), gradle, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.JAVA_VERSION_DECLARED)
                .anyMatch(f -> f.content().contains("21")));
    }

    @Test
    void shouldParseGradleModules() throws Exception {
        String settings = """
                rootProject.name = 'my-app'
                include 'core', 'api'
                """;
        Files.writeString(tempDir.resolve("settings.gradle"), settings, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_MODULE_DECLARED)
                .anyMatch(f -> f.content().contains("core")));
    }

    @Test
    void shouldParseGradleProjectVersion() throws Exception {
        String gradle = """
                version = '2.0.0'
                """;
        Files.writeString(tempDir.resolve("build.gradle"), gradle, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.PROJECT_VERSION_DECLARED)
                .anyMatch(f -> f.content().contains("2.0.0")));
    }

    @Test
    void shouldDetectBuildWrappers() throws Exception {
        Path mvnw = tempDir.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh", StandardCharsets.UTF_8);
        mvnw.toFile().setExecutable(true);
        Path gradlew = tempDir.resolve("gradlew");
        Files.writeString(gradlew, "#!/bin/sh", StandardCharsets.UTF_8);
        gradlew.toFile().setExecutable(true);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_WRAPPER_PRESENT)
                .anyMatch(f -> f.content().contains("MAVEN")));
        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.BUILD_WRAPPER_PRESENT)
                .anyMatch(f -> f.content().contains("GRADLE")));
    }

    @Test
    void shouldHandleKotlinGradleFiles() throws Exception {
        String gradleKts = """
                dependencies {
                    implementation("org.springframework:spring-core:6.1.0")
                }
                """;
        Files.writeString(tempDir.resolve("build.gradle.kts"), gradleKts, StandardCharsets.UTF_8);

        BuildCollector collector = new BuildCollector(createScanner(), createLimits());
        CollectionResult result = collector.collect(createContext(tempDir));

        assertTrue(result.facts().stream()
                .filter(f -> f.type() == com.hopeful117.devlogai.fact.entity.FactType.DEPENDENCY_DECLARED)
                .anyMatch(f -> f.content().contains("spring-core")));
    }
}
