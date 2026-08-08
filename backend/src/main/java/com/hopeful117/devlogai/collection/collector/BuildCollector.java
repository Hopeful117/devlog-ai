package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.build",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class BuildCollector extends AbstractFileCollector {
    private static final String VERSION = "build-v1";
    private static final String MAVEN_COORDINATES = "groupId=%s%nartifactId=%s";
    private static final Pattern XML_DEPENDENCY = Pattern.compile(
            "<dependency>\\s*+<groupId>\\s*+([^<]++)</groupId>\\s*+" +
                    "<artifactId>\\s*+([^<]++)</artifactId>");
    private static final Pattern XML_PLUGIN = Pattern.compile(
            "<plugin>\\s*+(?:<groupId>\\s*+([^<]++)</groupId>\\s*+)?" +
                    "<artifactId>\\s*+([^<]++)</artifactId>");
    private static final Pattern XML_MODULE = Pattern.compile("<module>\\s*+([^<]++)</module>");
    private static final Pattern XML_VERSION = Pattern.compile("<(maven.compiler.release|java.version|" +
            "maven.compiler.source|maven.compiler.target)>\\s*+([^<]++)</\\1>");
    private static final Pattern GRADLE_DEPENDENCY = Pattern.compile(
            "(?m)^[ \\t]*+(implementation|api|compileOnly|runtimeOnly|testImplementation)[ \\t]*+" +
                    "[('\\\"]++([A-Za-z0-9_.-]++):([A-Za-z0-9_.-]++)(?::([^'\"\\s)]++))?");
    private static final Pattern GRADLE_JAVA = Pattern.compile(
            "(?m)(?:sourceCompatibility|targetCompatibility|languageVersion)[ \\t]*+[=. ]++" +
                    "(?:JavaVersion\\.VERSION_|JavaLanguageVersion\\.of\\()?['\"]?(\\d[_\\d]*+)");
    private static final Pattern GRADLE_MODULE = Pattern.compile(
            "(?m)^[ \\t]*+include[ \\t]*+(?:\\([ \\t]*+)?['\"]([^'\"\\r\\n]++)['\"]");
    private static final Pattern GRADLE_VERSION = Pattern.compile(
            "(?m)^[ \\t]*+version[ \\t]*+=[ \\t]*+['\"]([^'\"\\r\\n]++)['\"]");

    public BuildCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        super(scanner, limits);
    }
    @Override public CollectorType type() { return CollectorType.BUILD; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        RepositoryScan scan = scan(context, this::isBuildFile);
        FactAccumulator facts = accumulator(context, scan);
        for (RepositoryFile file : scan.files()) {
            String path = file.relativePath();
            String name = fileName(path).toLowerCase(Locale.ROOT);
            if (name.equals("mvnw")) facts.add(FactType.BUILD_WRAPPER_PRESENT, "buildSystem=MAVEN", path);
            if (name.equals("gradlew")) facts.add(FactType.BUILD_WRAPPER_PRESENT, "buildSystem=GRADLE", path);
            if (file.content() == null) continue;
            if (name.equals("pom.xml")) parseMaven(file, facts);
            if (name.equals("build.gradle") || name.equals("build.gradle.kts")
                    || name.equals("settings.gradle") || name.equals("settings.gradle.kts")) {
                parseGradle(file, facts);
            }
        }
        return result(facts);
    }

    private boolean isBuildFile(String path) {
        return named(path, "pom.xml", "settings.xml", "build.gradle", "build.gradle.kts",
                "settings.gradle", "settings.gradle.kts", "gradle.properties", "mvnw", "gradlew");
    }

    private void parseMaven(RepositoryFile file, FactAccumulator facts) {
        String content = file.content();
        if (content.contains("<project") && !content.contains("</project>")) {
            facts.warning("MALFORMED_MAVEN_DESCRIPTOR",
                    "Maven descriptor is incomplete: " + file.relativePath());
        }
        facts.add(FactType.BUILD_SYSTEM_DETECTED, "buildSystem=MAVEN", file.relativePath());
        matches(XML_DEPENDENCY, content, matcher -> facts.add(FactType.DEPENDENCY_DECLARED,
                MAVEN_COORDINATES.formatted(clean(matcher.group(1)), clean(matcher.group(2))),
                file.relativePath()));
        matches(XML_PLUGIN, content, matcher -> facts.add(FactType.BUILD_PLUGIN_DECLARED,
                MAVEN_COORDINATES.formatted(
                        matcher.group(1) == null ? "org.apache.maven.plugins" : clean(matcher.group(1)),
                        clean(matcher.group(2))), file.relativePath()));
        matches(XML_MODULE, content, matcher -> facts.add(FactType.BUILD_MODULE_DECLARED,
                "module=" + clean(matcher.group(1)), file.relativePath()));
        matches(XML_VERSION, content, matcher -> facts.add(FactType.JAVA_VERSION_DECLARED,
                "javaVersion=" + clean(matcher.group(2)), file.relativePath()));
        String projectCoordinates = removeSections(content,
                "parent", "dependencies", "build", "properties");
        String projectVersion = firstTagValue(projectCoordinates, "version");
        if (projectVersion != null) facts.add(FactType.PROJECT_VERSION_DECLARED,
                "version=" + clean(projectVersion), file.relativePath());
    }

    private void parseGradle(RepositoryFile file, FactAccumulator facts) {
        String content = file.content();
        facts.add(FactType.BUILD_SYSTEM_DETECTED, "buildSystem=GRADLE", file.relativePath());
        int dependencyCount = matches(GRADLE_DEPENDENCY, content,
                matcher -> facts.add(FactType.DEPENDENCY_DECLARED,
                MAVEN_COORDINATES.formatted(matcher.group(2), matcher.group(3)),
                file.relativePath()));
        if (content.contains("dependencies") && dependencyCount == 0) {
            facts.warning("UNSUPPORTED_GRADLE_DECLARATION",
                    "No statically recognizable dependency in: " + file.relativePath());
        }
        matches(GRADLE_JAVA, content, matcher -> facts.add(FactType.JAVA_VERSION_DECLARED,
                "javaVersion=" + matcher.group(1).replace('_', '.'), file.relativePath()));
        matches(GRADLE_MODULE, content,
                matcher -> facts.add(FactType.BUILD_MODULE_DECLARED,
                        "module=" + matcher.group(1), file.relativePath()));
        matches(GRADLE_VERSION, content,
                matcher -> facts.add(FactType.PROJECT_VERSION_DECLARED,
                        "version=" + matcher.group(1), file.relativePath()));
    }

    private int matches(Pattern pattern, String content, java.util.function.Consumer<Matcher> consumer) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            consumer.accept(matcher);
            count++;
        }
        return count;
    }

    private String removeSections(String content, String... names) {
        String result = content;
        for (String name : names) {
            int start;
            while ((start = result.indexOf("<" + name + ">")) >= 0) {
                int end = result.indexOf("</" + name + ">", start);
                if (end < 0) break;
                result = result.substring(0, start)
                        + result.substring(end + name.length() + 3);
            }
        }
        return result;
    }

    private String firstTagValue(String content, String name) {
        String opening = "<" + name + ">";
        String closing = "</" + name + ">";
        int start = content.indexOf(opening);
        if (start < 0) return null;
        int valueStart = start + opening.length();
        int end = content.indexOf(closing, valueStart);
        return end < 0 ? null : content.substring(valueStart, end);
    }

    private String clean(String value) { return value.trim().replaceAll("\\s+", " "); }
}
