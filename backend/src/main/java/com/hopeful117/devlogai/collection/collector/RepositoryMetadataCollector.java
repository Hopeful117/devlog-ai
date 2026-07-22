package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "devlog.collection.collectors.repository-metadata",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class RepositoryMetadataCollector extends AbstractFileCollector {
    private static final String VERSION = "repository-metadata-v1";
    private static final List<String> SOURCE_DIRECTORIES = List.of(
            "src/main/java", "src/main/kotlin", "src/main/python", "src", "app", "lib");
    private static final Set<String> CONFIGURATION_FILES = Set.of(
            "application.properties", "application.yml", "application.yaml", ".editorconfig",
            "pom.xml", "build.gradle", "build.gradle.kts", "pyproject.toml", "package.json");

    public RepositoryMetadataCollector(SecureRepositoryScanner scanner, CollectorLimits limits) {
        super(scanner, limits);
    }

    @Override public CollectorType type() { return CollectorType.REPOSITORY_METADATA; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        RepositoryScan scan = scan(context, ignored -> false);
        FactAccumulator facts = accumulator(context, scan);
        facts.add(FactType.REPOSITORY_REVISION_RESOLVED,
                "resolvedRevision=" + context.resolvedRevision(), "git:" + context.resolvedRevision());
        facts.add(FactType.REPOSITORY_STRUCTURE_SUMMARY,
                "fileCount=%d%ndirectoryCount=%d".formatted(
                        scan.visitedFileCount(), scan.directoryCount()), "repository:/");

        Map<String, Integer> extensions = new LinkedHashMap<>();
        for (RepositoryFile file : scan.files()) {
            String path = file.relativePath();
            SOURCE_DIRECTORIES.stream().filter(directory -> path.equals(directory)
                    || path.startsWith(directory + "/")).findFirst().ifPresent(directory ->
                    facts.add(FactType.SOURCE_DIRECTORY_PRESENT,
                            "sourceDirectory=" + directory, directory));
            if (CONFIGURATION_FILES.contains(fileName(path).toLowerCase(Locale.ROOT))) {
                facts.add(FactType.CONFIGURATION_FILE_PRESENT,
                        "configurationFile=" + path, path);
            }
            int dot = fileName(path).lastIndexOf('.');
            if (dot > 0) extensions.merge(fileName(path).substring(dot + 1).toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        extensions.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(10)
                .forEach(entry -> facts.add(FactType.PRIMARY_FILE_EXTENSION,
                        "extension=%s%nfileCount=%d".formatted(entry.getKey(), entry.getValue()),
                        "repository:/"));
        long moduleDescriptors = scan.files().stream()
                .filter(file -> file.relativePath().endsWith("/pom.xml")
                        || file.relativePath().endsWith("/build.gradle")
                        || file.relativePath().endsWith("/build.gradle.kts"))
                .count();
        if (moduleDescriptors > 0) {
            facts.add(FactType.MULTI_MODULE_STRUCTURE_PRESENT,
                    "nestedBuildDescriptorCount=" + moduleDescriptors, "repository:/");
        }
        return result(facts);
    }
}
